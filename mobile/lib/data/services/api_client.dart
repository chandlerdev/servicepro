import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../config/app_config.dart';
import 'api_exception.dart';
import 'token_storage.dart';

/// Thin JSON wrapper around a single [http.Client].
///
/// * Prefixes paths with [baseUrl], encodes/decodes JSON (UTF-8), applies a
///   per-request timeout and adds `Authorization: Bearer <access>`.
/// * Maps every failure to [ApiException] (API `message` / `fieldErrors`, or a
///   friendly network / timeout message).
/// * On a 401 (except for the auth endpoints themselves) it calls
///   `/api/auth/refresh` once, stores the rotated tokens and retries the
///   original request once. Concurrent 401s share a single in-flight refresh.
///   If the refresh is rejected the tokens are cleared and [sessionExpired]
///   fires so the auth layer can sign the user out.
class ApiClient {
  ApiClient({
    required TokenStorage tokens,
    http.Client? client,
    String baseUrl = AppConfig.apiBaseUrl,
    this.timeout = defaultTimeout,
    bool log = kDebugMode,
  }) : _tokens = tokens,
       _client = client ?? http.Client(),
       _baseUrl = baseUrl.endsWith('/')
           ? baseUrl.substring(0, baseUrl.length - 1)
           : baseUrl,
       _log = log;

  static const defaultTimeout = Duration(seconds: 20);

  /// Paths that never carry a bearer token and never trigger a refresh.
  static const authFreePaths = {
    '/api/auth/login',
    '/api/auth/refresh',
    '/api/auth/logout',
    '/api/auth/forgot-password',
    '/api/auth/reset-password',
  };

  final TokenStorage _tokens;
  final http.Client _client;
  final String _baseUrl;
  final bool _log;
  final Duration timeout;

  final _expired = StreamController<void>.broadcast();

  /// The single in-flight refresh shared by concurrent 401 responses.
  Future<String?>? _refreshing;

  /// Fires when the refresh token was rejected and the tokens were cleared.
  Stream<void> get sessionExpired => _expired.stream;

  /// GET [path] → decoded JSON (or null for an empty body).
  Future<Object?> get(String path, {Map<String, Object?>? query}) =>
      _request('GET', path, query: query);

  /// POST [path] with an optional JSON [body] → decoded JSON (or null).
  Future<Object?> post(
    String path, {
    Object? body,
    Map<String, Object?>? query,
  }) => _request('POST', path, body: body, query: query);

  void close() {
    _client.close();
    _expired.close();
  }

  // ----------------------------------------------------------------- core

  Future<Object?> _request(
    String method,
    String path, {
    Map<String, Object?>? query,
    Object? body,
  }) async {
    final authFree = authFreePaths.contains(path);
    final token = authFree ? null : _tokens.accessToken;
    var res = await _send(method, path, query: query, body: body, token: token);

    if (res.statusCode == 401 && !authFree) {
      final fresh = await _refreshAfter401(token);
      if (fresh != null) {
        res = await _send(method, path, query: query, body: body, token: fresh);
        // Even a brand-new token is rejected: the account is gone/disabled.
        if (res.statusCode == 401) await _expire();
      }
    }
    return _decode(res);
  }

  Future<http.Response> _send(
    String method,
    String path, {
    Map<String, Object?>? query,
    Object? body,
    String? token,
  }) async {
    final req = http.Request(method, _uri(path, query))
      ..headers['Accept'] = 'application/json';
    if (token != null) req.headers['Authorization'] = 'Bearer $token';
    if (body != null) {
      req.headers['Content-Type'] = 'application/json; charset=utf-8';
      req.body = jsonEncode(body);
    }
    try {
      final streamed = await _client.send(req).timeout(timeout);
      final res = await http.Response.fromStream(streamed).timeout(timeout);
      if (_log) debugPrint('[api] $method $path → ${res.statusCode}');
      return res;
    } on TimeoutException {
      if (_log) debugPrint('[api] $method $path → timeout');
      throw ApiException.timeout();
    } on Exception catch (e) {
      // http.ClientException / SocketException / TLS errors.
      if (_log) debugPrint('[api] $method $path → $e');
      throw ApiException.network(e);
    }
  }

  Uri _uri(String path, Map<String, Object?>? query) {
    final uri = Uri.parse('$_baseUrl$path');
    final params = <String, String>{
      for (final e in (query ?? const <String, Object?>{}).entries)
        if (e.value != null) e.key: '${e.value}',
    };
    return params.isEmpty ? uri : uri.replace(queryParameters: params);
  }

  /// 2xx → decoded JSON, otherwise throws [ApiException].
  Object? _decode(http.Response res) {
    // Decode as UTF-8 regardless of the Content-Type charset (Spring often
    // omits it and package:http would then fall back to Latin-1).
    final text = utf8.decode(res.bodyBytes, allowMalformed: true);
    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw ApiException.fromResponse(res.statusCode, text);
    }
    if (text.trim().isEmpty) return null;
    try {
      return jsonDecode(text);
    } on FormatException {
      throw ApiException.badResponse();
    }
  }

  // -------------------------------------------------------------- refresh

  /// Returns a usable access token after a 401 obtained with [usedToken], or
  /// null if the session could not be renewed.
  Future<String?> _refreshAfter401(String? usedToken) {
    final current = _tokens.accessToken;
    // Another request already refreshed while this one was in flight.
    if (current != null && current != usedToken) return Future.value(current);
    return _refreshing ??= _refresh().whenComplete(() => _refreshing = null);
  }

  Future<String?> _refresh() async {
    final refreshToken = _tokens.refreshToken;
    if (refreshToken == null) {
      await _expire();
      return null;
    }

    final http.Response res;
    try {
      res = await _send(
        'POST',
        '/api/auth/refresh',
        body: {'refreshToken': refreshToken},
      );
    } on ApiException {
      // Network blip / timeout: keep the session so the user can retry.
      return null;
    }

    final code = res.statusCode;
    if (code >= 400 && code < 500) {
      // The server rejected the refresh token: the session is over.
      await _expire();
      return null;
    }
    if (code < 200 || code >= 300) return null; // 5xx: keep the session.

    try {
      final data = jsonDecode(utf8.decode(res.bodyBytes)) as Map;
      final access = data['accessToken'] as String;
      final rotated = data['refreshToken'] as String;
      // Signed out (or re-signed in) while refreshing: don't resurrect.
      if (_tokens.refreshToken != refreshToken) return _tokens.accessToken;
      await _tokens.save(accessToken: access, refreshToken: rotated);
      return access;
    } catch (_) {
      await _expire();
      return null;
    }
  }

  Future<void> _expire() async {
    await _tokens.clear();
    if (!_expired.isClosed) _expired.add(null);
  }
}

/// Casts a decoded JSON value to an object, or throws
/// [ApiException.badResponse].
Map<String, dynamic> jsonObject(Object? value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return Map<String, dynamic>.from(value);
  throw ApiException.badResponse();
}
