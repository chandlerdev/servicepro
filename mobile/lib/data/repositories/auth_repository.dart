import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/user.dart';
import '../services/api_client.dart';
import '../services/api_exception.dart';
import '../services/token_storage.dart';

enum AuthStatus { unknown, authenticated, unauthenticated }

/// Source of truth for the signed-in session. Notifies listeners whenever
/// [status], [user] or [message] change (the router listens to it).
class AuthRepository extends ChangeNotifier {
  AuthRepository({required ApiClient api, required TokenStorage tokens})
    : _api = api,
      _tokens = tokens {
    _expiredSub = _api.sessionExpired.listen((_) => _onSessionExpired());
  }

  static const adminMessage = 'Admins use the web panel';
  static const sessionExpiredMessage =
      'Your session has expired. Please sign in again.';

  final ApiClient _api;
  final TokenStorage _tokens;
  late final StreamSubscription<void> _expiredSub;

  AuthStatus _status = AuthStatus.unknown;
  User? _user;
  String? _message;

  AuthStatus get status => _status;
  bool get isAuthenticated => _status == AuthStatus.authenticated;

  /// May be null when authenticated offline (tokens present but /me failed
  /// with a network error).
  User? get user => _user;

  /// Shown on the login screen (e.g. "session expired").
  String? get message => _message;

  void _set(AuthStatus status, {User? user, String? message}) {
    _status = status;
    _user = user;
    _message = message;
    notifyListeners();
  }

  void _onSessionExpired() {
    if (_status != AuthStatus.unauthenticated) {
      _set(AuthStatus.unauthenticated, message: sessionExpiredMessage);
    }
  }

  /// Restores a stored session on app start via `GET /api/auth/me`.
  Future<void> restore() async {
    await _tokens.load();
    if (_tokens.refreshToken == null) {
      _set(AuthStatus.unauthenticated);
      return;
    }
    try {
      final me = await this.me();
      if (!me.isTechnician) {
        await logout(notify: false);
        _set(AuthStatus.unauthenticated, message: adminMessage);
        return;
      }
      _set(AuthStatus.authenticated, user: me);
    } on ApiException catch (e) {
      if (e.statusCode == 401 || e.statusCode == 403) {
        await _tokens.clear();
        _set(AuthStatus.unauthenticated);
      } else {
        // Offline / server down: keep the stored session, screens will retry.
        _set(AuthStatus.authenticated);
      }
    }
  }

  /// `POST /api/auth/login`. Only technicians may sign in; for admins the
  /// issued refresh token is revoked and never stored. Throws [ApiException].
  Future<void> login(String email, String password) async {
    final auth = AuthResponse.fromJson(
      jsonObject(
        await _api.post(
          '/api/auth/login',
          body: {'email': email.trim(), 'password': password},
        ),
      ),
    );
    if (!auth.user.isTechnician) {
      await _revoke(auth.refreshToken);
      throw ApiException(adminMessage, statusCode: 403);
    }
    await _tokens.save(
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
    );
    _set(AuthStatus.authenticated, user: auth.user);
  }

  /// `POST /api/auth/forgot-password`: emails a 6-digit code. Returns the
  /// server's message (the same whether or not the account exists).
  Future<String> requestPasswordReset(String email) async {
    final res = jsonObject(
      await _api.post(
        '/api/auth/forgot-password',
        body: {'email': email.trim()},
      ),
    );
    return res['message'] as String? ?? 'A code has been sent to your email.';
  }

  /// `POST /api/auth/reset-password` with the emailed code. Throws
  /// [ApiException] (e.g. wrong or expired code).
  Future<void> resetPassword(String email, String code, String newPassword) =>
      _api.post(
        '/api/auth/reset-password',
        body: {
          'email': email.trim(),
          'code': code.trim(),
          'newPassword': newPassword,
        },
      );

  /// `GET /api/auth/me` → [User].
  Future<User> me() async =>
      User.fromJson(jsonObject(await _api.get('/api/auth/me')));

  /// `POST /api/auth/logout` (best effort) and drops the local tokens.
  Future<void> logout({bool notify = true}) async {
    final token = _tokens.refreshToken;
    if (token != null) await _revoke(token);
    await _tokens.clear();
    if (notify) _set(AuthStatus.unauthenticated);
  }

  Future<void> _revoke(String refreshToken) async {
    try {
      await _api.post('/api/auth/logout', body: {'refreshToken': refreshToken});
    } catch (_) {
      // Ignore: local tokens are dropped regardless.
    }
  }

  @override
  void dispose() {
    _expiredSub.cancel();
    super.dispose();
  }
}
