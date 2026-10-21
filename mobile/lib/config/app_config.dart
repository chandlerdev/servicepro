import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// App configuration.
class AppConfig {
  AppConfig._();

  /// Backend base URL — the live server (Apache forwards /api to Spring Boot).
  static const String apiBaseUrl = 'https://teksupportsquad.desss-portfolio.com';

  static const String appName = 'ServProWare';

  /// Average driving speed used for "~N min drive" estimates on the map.
  static const double avgSpeedKmh = 32;

  /// How often the stop list auto-refreshes while the app is foregrounded.
  static const Duration autoRefreshInterval = Duration(seconds: 60);

  static const MethodChannel _channel = MethodChannel('servicepros/config');

  static bool? _mapsConfigured;

  /// Whether a Google Maps API key has been configured natively
  /// (Android manifest placeholder / iOS Info.plist `GMSApiKey`).
  /// Rendering a GoogleMap without a key crashes on iOS, so the Map tab
  /// falls back to a list when this is false.
  static Future<bool> isMapsConfigured() async {
    if (_mapsConfigured != null) return _mapsConfigured!;
    try {
      _mapsConfigured =
          await _channel.invokeMethod<bool>('isMapsConfigured') ?? false;
    } catch (e) {
      debugPrint('Maps config check failed: $e');
      _mapsConfigured = false;
    }
    return _mapsConfigured!;
  }
}
