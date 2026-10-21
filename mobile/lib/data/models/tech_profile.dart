import 'json_utils.dart';
import 'user.dart';
import 'stop_models.dart';

/// `{ id, name, vehicleName, vehiclePlate, city }`
class TechRoute {
  const TechRoute({
    required this.id,
    required this.name,
    required this.vehicleName,
    required this.vehiclePlate,
    required this.city,
  });

  final int id;
  final String name;
  final String? vehicleName;
  final String? vehiclePlate;
  final String? city;

  factory TechRoute.fromJson(Map<String, dynamic> json) => TechRoute(
    id: jInt(json['id']) ?? 0,
    name: jString(json['name']) ?? '',
    vehicleName: jString(json['vehicleName']),
    vehiclePlate: jString(json['vehiclePlate']),
    city: jString(json['city']),
  );
}

/// GET /api/tech/me → `{ user: User, technician: Technician, routes: TechRoute[] }`
class TechProfile {
  const TechProfile({
    required this.user,
    required this.technician,
    required this.routes,
  });

  final User user;
  final Technician? technician;
  final List<TechRoute> routes;

  factory TechProfile.fromJson(Map<String, dynamic> json) => TechProfile(
    user: User.fromJson(jMap(json['user'])),
    technician: json['technician'] is Map
        ? Technician.fromJson(jMap(json['technician']))
        : null,
    routes: jList(json['routes']).map(TechRoute.fromJson).toList(),
  );
}
