import 'dart:async';
import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:servicepros_tech/data/services/api_client.dart';
import 'package:servicepros_tech/data/services/api_exception.dart';
import 'package:servicepros_tech/data/services/token_storage.dart';

/// Fake backend: access token "new" is valid, anything else → 401.
class _FakeServer {
  int refreshCalls = 0;
  bool refreshFails = false;
  final List<http.Request> seen = [];

  Future<http.Response> handle(http.Request req) async {
    seen.add(req);
    final path = req.url.path;
    if (path == '/api/auth/refresh') {
      refreshCalls++;
      await Future<void>.delayed(const Duration(milliseconds: 20));
      if (refreshFails) {
        return _json(401, {'status': 401, 'message': 'Invalid refresh token'});
      }
      expect(jsonDecode(req.body), {'refreshToken': 'refresh1'});
      return _json(200, {
        'accessToken': 'new',
        'refreshToken': 'refresh2',
        'tokenType': 'Bearer',
        'expiresIn': 900,
        'user': {
          'id': 1,
          'name': 'T',
          'email': 't@x',
          'role': 'TECHNICIAN',
          'technicianId': 1,
          'active': true,
        },
      });
    }
    if (path == '/api/slow') {
      // Answers after a concurrent request has already refreshed.
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    if (req.headers['Authorization'] != 'Bearer new') {
      return _json(401, {'status': 401, 'message': 'Unauthorized'});
    }
    return _json(200, {'ok': true, 'path': path, 'query': req.url.query});
  }

  http.Response _json(int status, Object body) => http.Response.bytes(
    utf8.encode(jsonEncode(body)),
    status,
    headers: {'content-type': 'application/json'},
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _FakeServer server;
  late TokenStorage tokens;
  late int expiredCalls;
  late ApiClient api;
  late StreamSubscription<void> sub;

  ApiClient buildClient(MockClientHandler handler, {Duration? timeout}) =>
      ApiClient(
        tokens: tokens,
        client: MockClient(handler),
        baseUrl: 'http://test',
        timeout: timeout ?? ApiClient.defaultTimeout,
        log: false,
      );

  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({
      'sp_access_token': 'old',
      'sp_refresh_token': 'refresh1',
    });
    server = _FakeServer();
    tokens = TokenStorage();
    await tokens.load();
    expiredCalls = 0;
    api = buildClient(server.handle);
    sub = api.sessionExpired.listen((_) => expiredCalls++);
  });

  tearDown(() async {
    await sub.cancel();
    api.close();
  });

  test('401 → refresh once → retry succeeds, tokens rotated', () async {
    final res = jsonObject(
      await api.get('/api/tech/stops', query: {'date': '2026-10-01'}),
    );
    expect(res['ok'], isTrue);
    expect(res['query'], 'date=2026-10-01');
    expect(server.refreshCalls, 1);
    expect(tokens.accessToken, 'new');
    expect(tokens.refreshToken, 'refresh2');
    // original (old token) → refresh → retry (new token)
    expect(server.seen.map((r) => r.url.path), [
      '/api/tech/stops',
      '/api/auth/refresh',
      '/api/tech/stops',
    ]);
    expect(server.seen.first.headers['Authorization'], 'Bearer old');
    expect(server.seen[1].headers.containsKey('Authorization'), isFalse);
    expect(server.seen.last.headers['Authorization'], 'Bearer new');
    await pumpEventQueue();
    expect(expiredCalls, 0);
  });

  test('concurrent 401s share a single refresh', () async {
    final results = await Future.wait([
      api.get('/api/tech/stops'),
      api.get('/api/tech/notifications'),
      api.get('/api/tech/me'),
      api.post('/api/tech/appointments/1/on-my-way'),
    ]);
    expect(results.every((r) => jsonObject(r)['ok'] == true), isTrue);
    expect(server.refreshCalls, 1);
    expect(tokens.accessToken, 'new');
  });

  test('a 401 arriving after a finished refresh reuses the new token', () async {
    final results = await Future.wait([
      api.get('/api/slow'), // sent with "old", 401 arrives after refresh
      api.get('/api/tech/me'), // 401 → refresh → retry
    ]);
    expect(results.every((r) => jsonObject(r)['ok'] == true), isTrue);
    expect(server.refreshCalls, 1);
    final slow = server.seen.where((r) => r.url.path == '/api/slow').toList();
    expect(slow.map((r) => r.headers['Authorization']), [
      'Bearer old',
      'Bearer new',
    ]);
  });

  test('refresh rejected → tokens cleared and session expired', () async {
    server.refreshFails = true;
    await expectLater(
      api.get('/api/tech/me'),
      throwsA(
        isA<ApiException>()
            .having((e) => e.statusCode, 'status', 401)
            .having((e) => e.message, 'message', 'Unauthorized'),
      ),
    );
    expect(server.refreshCalls, 1);
    expect(tokens.accessToken, isNull);
    expect(tokens.refreshToken, isNull);
    await pumpEventQueue();
    expect(expiredCalls, 1);
  });

  test('concurrent 401s with a rejected refresh expire the session once', () {
    server.refreshFails = true;
    return Future.wait([
      expectLater(api.get('/api/tech/me'), throwsA(isA<ApiException>())),
      expectLater(api.get('/api/tech/stops'), throwsA(isA<ApiException>())),
    ]).then((_) async {
      expect(server.refreshCalls, 1);
      await pumpEventQueue();
      expect(expiredCalls, 1);
    });
  });

  test('login errors are not intercepted and expose the API message', () async {
    await expectLater(
      api.post('/api/auth/login', body: {'email': 'a', 'password': 'b'}),
      throwsA(
        isA<ApiException>()
            .having((e) => e.message, 'message', 'Unauthorized')
            .having((e) => e.statusCode, 'status', 401),
      ),
    );
    expect(server.refreshCalls, 0);
    final login = server.seen.single;
    expect(login.headers.containsKey('Authorization'), isFalse);
    expect(login.headers['Content-Type'], contains('application/json'));
    expect(jsonDecode(login.body), {'email': 'a', 'password': 'b'});
    await pumpEventQueue();
    expect(expiredCalls, 0);
    expect(tokens.refreshToken, 'refresh1');
  });

  test('validation errors map message and fieldErrors', () async {
    final c = buildClient(
      (_) async => http.Response(
        jsonEncode({
          'status': 400,
          'message': 'Validation failed',
          'fieldErrors': {'minutes': 'must be at least 5'},
        }),
        400,
      ),
    );
    await expectLater(
      c.post('/api/tech/appointments/1/delay', body: {'minutes': 1}),
      throwsA(
        isA<ApiException>()
            .having((e) => e.statusCode, 'status', 400)
            .having((e) => e.fieldErrors, 'fields', {
              'minutes': 'must be at least 5',
            })
            .having(
              (e) => e.detailed,
              'detailed',
              'Validation failed\nminutes: must be at least 5',
            ),
      ),
    );
    c.close();
  });

  test('network and timeout errors get friendly messages', () async {
    final offline = buildClient(
      (_) async => throw http.ClientException('Connection refused'),
    );
    await expectLater(
      offline.get('/api/tech/me'),
      throwsA(
        isA<ApiException>()
            .having((e) => e.statusCode, 'status', isNull)
            .having((e) => e.message, 'message', contains("Can't reach")),
      ),
    );
    offline.close();

    final slow = buildClient((_) async {
      await Future<void>.delayed(const Duration(milliseconds: 200));
      return http.Response('{}', 200);
    }, timeout: const Duration(milliseconds: 20));
    await expectLater(
      slow.get('/api/tech/me'),
      throwsA(
        isA<ApiException>().having(
          (e) => e.message,
          'message',
          contains('took too long'),
        ),
      ),
    );
    slow.close();
  });

  test('decodes UTF-8 bodies without a charset and empty 204s', () async {
    final c = buildClient(
      (req) async => req.url.path == '/empty'
          ? http.Response('', 204)
          : http.Response.bytes(
              utf8.encode(jsonEncode({'name': 'José — ⚡'})),
              200,
              headers: {'content-type': 'application/json'},
            ),
    );
    await tokens.save(accessToken: 'new', refreshToken: 'refresh1');
    expect(jsonObject(await c.get('/x'))['name'], 'José — ⚡');
    expect(await c.post('/empty'), isNull);
    c.close();
  });
}
