import 'dart:async';
import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:servicepros_tech/data/models/app_notification.dart';
import 'package:servicepros_tech/data/models/page_result.dart';
import 'package:servicepros_tech/data/models/stop_models.dart';
import 'package:servicepros_tech/data/repositories/alerts_repository.dart';
import 'package:servicepros_tech/data/repositories/auth_repository.dart';
import 'package:servicepros_tech/data/repositories/stops_repository.dart';
import 'package:servicepros_tech/data/services/api_client.dart';
import 'package:servicepros_tech/data/services/api_exception.dart';
import 'package:servicepros_tech/data/services/token_storage.dart';
import 'package:servicepros_tech/ui/alerts/view_model/alerts_view_model.dart';
import 'package:servicepros_tech/ui/home/view_model/home_view_model.dart';
import 'package:servicepros_tech/ui/stops/view_model/stops_view_model.dart';
import 'package:servicepros_tech/utils/result.dart';

TechStops _stops(String date, {int stops = 1}) => TechStops.fromJson({
  'date': date,
  'office': {'name': 'HQ', 'address': '1 Main', 'lat': 30.0, 'lng': -97.0},
  'routes': [
    {
      'routeId': 1,
      'routeName': 'North',
      'optimized': true,
      'stops': [
        for (var i = 1; i <= stops; i++)
          {
            'appointmentId': 10 + i,
            'order': i,
            'customerName': 'C$i',
            'status': 'SCHEDULED',
          },
      ],
    },
  ],
});

class FakeStopsRepository implements StopsRepository {
  final List<DateTime> fetched = [];
  Object? fetchError;
  Completer<Stop>? onMyWayGate;
  Object? actionError;

  @override
  Stream<void> get changes => const Stream.empty();

  @override
  Future<TechStops> fetchStops(DateTime date) async {
    fetched.add(date);
    await Future<void>.delayed(Duration.zero);
    if (fetchError != null) throw fetchError!;
    return _stops(StopsRepository.apiDate(date));
  }

  @override
  Future<Stop> onMyWay(int appointmentId) async {
    if (actionError != null) throw actionError!;
    return onMyWayGate!.future;
  }

  @override
  Future<int> reportDelay(int appointmentId, int minutes, String? reason) async {
    if (actionError != null) throw actionError!;
    return 3;
  }

  @override
  Future<CompleteResult> complete(int appointmentId, String? notes) async =>
      CompleteResult.fromJson({
        'appointment': {'appointmentId': appointmentId, 'status': 'COMPLETED'},
      });

  @override
  void dispose() {}
}

class FakeAlertsRepository implements AlertsRepository {
  int totalPages = 3;
  Object? error;
  final List<int> pages = [];

  @override
  Future<PageResult<AppNotification>> fetch({int page = 0, int size = 20}) async {
    pages.add(page);
    if (error != null) throw error!;
    return PageResult(
      content: [
        for (var i = 0; i < 2; i++)
          AppNotification.fromJson({'id': page * 10 + i, 'type': 'DELAY'}),
      ],
      page: page,
      size: size,
      totalElements: totalPages * 2,
      totalPages: totalPages,
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late ApiClient api;
  late AuthRepository auth;
  late HomeViewModel home;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    final tokens = TokenStorage();
    api = ApiClient(
      tokens: tokens,
      client: MockClient((_) async => http.Response(jsonEncode({}), 500)),
      baseUrl: 'http://test',
      log: false,
    );
    auth = AuthRepository(api: api, tokens: tokens);
    home = HomeViewModel(auth: auth);
  });

  tearDown(() {
    home.dispose();
    auth.dispose();
    api.close();
  });

  group('StopsViewModel', () {
    late FakeStopsRepository repo;
    late StopsViewModel vm;

    setUp(() {
      repo = FakeStopsRepository();
      vm = StopsViewModel(repository: repo, home: home);
    });

    tearDown(() => vm.dispose());

    test('load success exposes data and toggles isLoading', () async {
      final states = <bool>[];
      vm.addListener(() => states.add(vm.isLoading));
      await vm.load();
      expect(states, [true, false]);
      expect(vm.data!.stopCount, 1);
      expect(vm.error, isNull);
      expect(repo.fetched.single, home.selectedDate);
    });

    test('load error exposes the ApiException; retry clears it', () async {
      repo.fetchError = ApiException('Server error (500). Please try again.');
      await vm.load();
      expect(vm.isLoading, isFalse);
      expect(vm.data, isNull);
      expect(vm.error!.message, startsWith('Server error'));

      repo.fetchError = null;
      await vm.load();
      expect(vm.error, isNull);
      expect(vm.data, isNotNull);
    });

    test('refresh failure keeps the list and returns Err', () async {
      await vm.load();
      final before = vm.data;
      repo.fetchError = ApiException('offline');
      final r = await vm.refresh();
      expect(r, isA<Err<void>>());
      expect((r as Err).error.message, 'offline');
      expect(vm.data, same(before));
      expect(vm.error, isNull);
    });

    test('changing the day in HomeViewModel reloads that day', () async {
      await vm.load();
      home.nextDay();
      expect(vm.isLoading, isTrue);
      await pumpEventQueue();
      expect(repo.fetched.last, home.selectedDate);
      expect(vm.data!.date, StopsRepository.apiDate(home.selectedDate));
      expect(vm.isLoading, isFalse);
    });

    test('per-card busy state during an action, then refresh', () async {
      await vm.load();
      repo.onMyWayGate = Completer<Stop>();
      final future = vm.onMyWay(11);
      expect(vm.busyAction(11), StopAction.onMyWay);
      expect(vm.isBusy(11), isTrue);
      expect(vm.isBusy(12), isFalse);

      // A second tap on the same card is rejected while busy.
      expect(await vm.delay(11, 15, null), isA<Err<int>>());

      final fetchesBefore = repo.fetched.length;
      repo.onMyWayGate!.complete(
        Stop.fromJson({'appointmentId': 11, 'status': 'EN_ROUTE'}),
      );
      final r = await future;
      expect(r, isA<Ok<Stop>>());
      expect((r as Ok<Stop>).value.status, 'EN_ROUTE');
      expect(vm.isBusy(11), isFalse);
      expect(repo.fetched.length, fetchesBefore + 1); // list refreshed
    });

    test('actions return values for the View and errors as Err', () async {
      await vm.load();
      final delay = await vm.delay(11, 30, 'Traffic');
      expect((delay as Ok<int>).value, 3);
      final done = await vm.complete(11, null);
      expect((done as Ok<CompleteResult>).value.appointment.status, 'COMPLETED');

      repo.actionError = ApiException('Conflict', statusCode: 409);
      final failed = await vm.delay(11, 30, null);
      expect((failed as Err<int>).error.statusCode, 409);
      expect(vm.isBusy(11), isFalse);
    });

    test('auto-refresh reloads after a failed first load', () async {
      repo.fetchError = ApiException('offline');
      await vm.load();
      repo.fetchError = null;
      await vm.autoRefresh();
      expect(vm.error, isNull);
      expect(vm.data, isNotNull);
    });
  });

  group('HomeViewModel', () {
    test('navigateToStop opens the map; other tabs clear the focus', () {
      home.navigateToStop(42);
      expect(home.tab, HomeTab.map);
      expect(home.mapFocus, 42);
      home.selectTab(HomeTab.alerts);
      expect(home.mapFocus, isNull);
    });
  });

  group('AlertsViewModel', () {
    test('loads first page and appends more until the last page', () async {
      final repo = FakeAlertsRepository();
      final vm = AlertsViewModel(repository: repo);
      await vm.load();
      expect(vm.items.length, 2);
      expect(vm.hasMore, isTrue);
      await vm.loadMore();
      await vm.loadMore();
      expect(vm.items.length, 6);
      expect(vm.hasMore, isFalse);
      await vm.loadMore(); // no-op
      expect(repo.pages, [0, 1, 2]);
      vm.dispose();
    });

    test('loadMore error is kept for the retry footer', () async {
      final repo = FakeAlertsRepository();
      final vm = AlertsViewModel(repository: repo);
      await vm.load();
      repo.error = ApiException('offline');
      await vm.loadMore();
      expect(vm.loadMoreError, isNotNull);
      expect(vm.loadingMore, isFalse);
      expect(vm.items.length, 2);
      vm.dispose();
    });
  });
}
