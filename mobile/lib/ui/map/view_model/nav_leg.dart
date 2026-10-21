import '../../../data/models/stop_models.dart';
import '../../core/utils/geo.dart';

/// Leg info for navigating to a stop: distance from the previous stop on the
/// same route (or the depot for the first stop).
class NavLeg {
  const NavLeg({required this.stop, required this.km, required this.minutes});

  final Stop stop;
  final double? km;
  final int? minutes;

  static NavLeg? forStop(TechStops data, int appointmentId) {
    final rs = data.findStop(appointmentId);
    if (rs == null) return null;
    final stops = rs.route.stops;
    final idx = stops.indexWhere((s) => s.appointmentId == appointmentId);
    double? fromLat = data.office.lat, fromLng = data.office.lng;
    for (var i = idx - 1; i >= 0; i--) {
      if (stops[i].hasCoords) {
        fromLat = stops[i].lat;
        fromLng = stops[i].lng;
        break;
      }
    }
    final s = rs.stop;
    if (!s.hasCoords || fromLat == null || fromLng == null) {
      return NavLeg(stop: s, km: null, minutes: null);
    }
    final km = haversineKm(fromLat, fromLng, s.lat!, s.lng!);
    return NavLeg(stop: s, km: km, minutes: driveMinutes(km));
  }
}
