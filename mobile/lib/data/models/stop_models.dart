import 'json_utils.dart';

/// AppointmentStatus values.
class AppointmentStatus {
  AppointmentStatus._();
  static const scheduled = 'SCHEDULED';
  static const confirmed = 'CONFIRMED';
  static const enRoute = 'EN_ROUTE';
  static const delayed = 'DELAYED';
  static const completed = 'COMPLETED';
  static const cancelled = 'CANCELLED';
}

/// `Technician = { id, name, phone, email, skills, active, hasLogin }`
class Technician {
  const Technician({
    required this.id,
    required this.name,
    required this.phone,
    required this.email,
    required this.skills,
    required this.active,
    required this.hasLogin,
  });

  final int id;
  final String name;
  final String phone;
  final String email;
  final String skills;
  final bool active;
  final bool hasLogin;

  factory Technician.fromJson(Map<String, dynamic> json) => Technician(
    id: jInt(json['id']) ?? 0,
    name: jString(json['name']) ?? '',
    phone: jString(json['phone']) ?? '',
    email: jString(json['email']) ?? '',
    skills: jString(json['skills']) ?? '',
    active: json['active'] as bool? ?? true,
    hasLogin: json['hasLogin'] as bool? ?? false,
  );
}

/// `office: { name, address, lat, lng }`
class Office {
  const Office({
    required this.name,
    required this.address,
    required this.lat,
    required this.lng,
  });

  final String name;
  final String address;
  final double? lat;
  final double? lng;

  bool get hasCoords => lat != null && lng != null;

  factory Office.fromJson(Map<String, dynamic> json) => Office(
    name: jString(json['name']) ?? 'Office',
    address: jString(json['address']) ?? '',
    lat: jDouble(json['lat']),
    lng: jDouble(json['lng']),
  );
}

/// ```
/// Stop = { appointmentId, order, customerId, customerName, customerPhone,
///   address, lat, lng, taskName, durationMinutes, status, notes,
///   plannedArrival, plannedEnd, legKm }
/// ```
class Stop {
  const Stop({
    required this.appointmentId,
    required this.order,
    required this.customerId,
    required this.customerName,
    required this.customerPhone,
    required this.address,
    required this.lat,
    required this.lng,
    required this.taskName,
    required this.durationMinutes,
    required this.status,
    required this.notes,
    required this.plannedArrival,
    required this.plannedEnd,
    required this.legKm,
  });

  final int appointmentId;
  final int order;
  final int customerId;
  final String customerName;
  final String customerPhone;
  final String address;
  final double? lat;
  final double? lng;
  final String taskName;
  final int durationMinutes;
  final String status;
  final String? notes;
  final String? plannedArrival;
  final String? plannedEnd;
  final double? legKm;

  bool get hasCoords => lat != null && lng != null;
  bool get isCompleted => status == AppointmentStatus.completed;
  bool get isCancelled => status == AppointmentStatus.cancelled;
  bool get isDelayed => status == AppointmentStatus.delayed;
  bool get isOpen => !isCompleted && !isCancelled;
  bool get canStartTravel =>
      status == AppointmentStatus.scheduled ||
      status == AppointmentStatus.confirmed;

  factory Stop.fromJson(Map<String, dynamic> json) => Stop(
    appointmentId: jInt(json['appointmentId']) ?? 0,
    order: jInt(json['order']) ?? 0,
    customerId: jInt(json['customerId']) ?? 0,
    customerName: jString(json['customerName']) ?? '',
    customerPhone: jString(json['customerPhone']) ?? '',
    address: jString(json['address']) ?? '',
    lat: jDouble(json['lat']),
    lng: jDouble(json['lng']),
    taskName: jString(json['taskName']) ?? '',
    durationMinutes: jInt(json['durationMinutes']) ?? 0,
    status: jString(json['status']) ?? AppointmentStatus.scheduled,
    notes: jString(json['notes']),
    plannedArrival: jString(json['plannedArrival']),
    plannedEnd: jString(json['plannedEnd']),
    legKm: jDouble(json['legKm']),
  );
}

/// ```
/// RoutePlan = { routeId, routeName, vehicleName, vehiclePlate,
///   technicians: {id,name}[], startTime, optimized, stale, totalKm, endTime,
///   optimizedAt, notifiedAt, googleMapsUrls: string[], stops: Stop[] }
/// ```
class RoutePlan {
  const RoutePlan({
    required this.routeId,
    required this.routeName,
    required this.vehicleName,
    required this.vehiclePlate,
    required this.technicians,
    required this.startTime,
    required this.optimized,
    required this.stale,
    required this.totalKm,
    required this.endTime,
    required this.optimizedAt,
    required this.notifiedAt,
    required this.googleMapsUrls,
    required this.stops,
  });

  final int routeId;
  final String routeName;
  final String? vehicleName;
  final String? vehiclePlate;
  final List<({int id, String name})> technicians;
  final String? startTime;
  final bool optimized;
  final bool stale;
  final double? totalKm;
  final String? endTime;
  final String? optimizedAt;
  final String? notifiedAt;
  final List<String> googleMapsUrls;
  final List<Stop> stops;

  factory RoutePlan.fromJson(Map<String, dynamic> json) {
    final stops = jList(json['stops']).map(Stop.fromJson).toList()
      ..sort((a, b) => a.order.compareTo(b.order));
    return RoutePlan(
      routeId: jInt(json['routeId']) ?? 0,
      routeName: jString(json['routeName']) ?? '',
      vehicleName: jString(json['vehicleName']),
      vehiclePlate: jString(json['vehiclePlate']),
      technicians: jList(json['technicians'])
          .map((t) => (id: jInt(t['id']) ?? 0, name: jString(t['name']) ?? ''))
          .toList(),
      startTime: jString(json['startTime']),
      optimized: json['optimized'] as bool? ?? false,
      stale: json['stale'] as bool? ?? false,
      totalKm: jDouble(json['totalKm']),
      endTime: jString(json['endTime']),
      optimizedAt: jString(json['optimizedAt']),
      notifiedAt: jString(json['notifiedAt']),
      googleMapsUrls:
          (json['googleMapsUrls'] as List?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      stops: stops,
    );
  }
}

/// A stop with the route it belongs to.
typedef RouteStop = ({RoutePlan route, Stop stop});

/// GET /api/tech/stops →
/// `{ date, office:{name,address,lat,lng}, technician: Technician, routes: RoutePlan[] }`
class TechStops {
  const TechStops({
    required this.date,
    required this.office,
    required this.technician,
    required this.routes,
  });

  final String date;
  final Office office;
  final Technician? technician;
  final List<RoutePlan> routes;

  /// All stops in route order (routes in API order).
  List<RouteStop> get allStops => [
    for (final r in routes)
      for (final s in r.stops) (route: r, stop: s),
  ];

  int get stopCount => allStops.where((e) => !e.stop.isCancelled).length;

  int get completedCount => allStops.where((e) => e.stop.isCompleted).length;

  RouteStop? get firstOpenStop {
    for (final e in allStops) {
      if (e.stop.isOpen) return e;
    }
    return null;
  }

  RouteStop? findStop(int appointmentId) {
    for (final e in allStops) {
      if (e.stop.appointmentId == appointmentId) return e;
    }
    return null;
  }

  factory TechStops.fromJson(Map<String, dynamic> json) => TechStops(
    date: jString(json['date']) ?? '',
    office: Office.fromJson(jMap(json['office'])),
    technician: json['technician'] is Map
        ? Technician.fromJson(jMap(json['technician']))
        : null,
    routes: jList(json['routes']).map(RoutePlan.fromJson).toList(),
  );
}

/// POST /api/tech/appointments/{id}/complete → `{ appointment: Stop }`
class CompleteResult {
  const CompleteResult({required this.appointment});

  final Stop appointment;

  factory CompleteResult.fromJson(Map<String, dynamic> json) =>
      CompleteResult(appointment: Stop.fromJson(jMap(json['appointment'])));
}
