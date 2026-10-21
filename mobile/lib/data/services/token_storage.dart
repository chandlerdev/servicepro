import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persists access / refresh tokens in the platform keystore (Keychain on iOS,
/// EncryptedSharedPreferences/Keystore on Android) with an in-memory cache.
class TokenStorage {
  TokenStorage([FlutterSecureStorage? storage])
    : _storage = storage ?? const FlutterSecureStorage();

  static const _kAccess = 'sp_access_token';
  static const _kRefresh = 'sp_refresh_token';

  final FlutterSecureStorage _storage;
  String? _access;
  String? _refresh;
  bool _loaded = false;

  String? get accessToken => _access;
  String? get refreshToken => _refresh;

  Future<void> load() async {
    if (_loaded) return;
    try {
      _access = await _storage.read(key: _kAccess);
      _refresh = await _storage.read(key: _kRefresh);
    } catch (_) {
      // Corrupted keystore entries (e.g. after a backup restore): start clean.
      await clear();
    }
    _loaded = true;
  }

  Future<void> save({
    required String accessToken,
    required String refreshToken,
  }) async {
    _access = accessToken;
    _refresh = refreshToken;
    _loaded = true;
    await _storage.write(key: _kAccess, value: accessToken);
    await _storage.write(key: _kRefresh, value: refreshToken);
  }

  Future<void> clear() async {
    _access = null;
    _refresh = null;
    try {
      await _storage.delete(key: _kAccess);
      await _storage.delete(key: _kRefresh);
    } catch (_) {}
  }
}
