import 'dart:math' as math;

import '../../../config/app_config.dart';

/// Great-circle distance in km between two lat/lng points.
double haversineKm(double lat1, double lng1, double lat2, double lng2) {
  const r = 6371.0;
  double rad(double d) => d * math.pi / 180;
  final dLat = rad(lat2 - lat1);
  final dLng = rad(lng2 - lng1);
  final a =
      math.pow(math.sin(dLat / 2), 2) +
      math.cos(rad(lat1)) *
          math.cos(rad(lat2)) *
          math.pow(math.sin(dLng / 2), 2);
  return 2 * r * math.asin(math.sqrt(a.toDouble()));
}

/// Rough driving time at [AppConfig.avgSpeedKmh], minimum 2 minutes.
int driveMinutes(double km, {double speedKmh = AppConfig.avgSpeedKmh}) =>
    math.max(2, (km / speedKmh * 60).round());

/// Google Maps turn-by-turn hand-off URL.
Uri googleMapsDirections(double lat, double lng) => Uri.parse(
  'https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving',
);
