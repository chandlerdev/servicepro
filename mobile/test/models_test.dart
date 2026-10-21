import 'package:flutter_test/flutter_test.dart';
import 'package:servicepros_tech/data/models/page_result.dart';
import 'package:servicepros_tech/data/models/app_notification.dart';
import 'package:servicepros_tech/data/models/user.dart';
import 'package:servicepros_tech/ui/map/view_model/nav_leg.dart';
import 'package:servicepros_tech/data/models/tech_profile.dart';
import 'package:servicepros_tech/data/models/stop_models.dart';

Map<String, dynamic> _stop(
  int id,
  int order,
  String status, {
  double? lat,
  double? lng,
  String? eta,
}) => {
  'appointmentId': id,
  'order': order,
  'customerId': 100 + id,
  'customerName': 'Customer $id',
  'customerPhone': '+15125550$id',
  'address': '$id Main St, Austin, TX',
  'lat': lat,
  'lng': lng,
  'taskName': 'AC Tune-up',
  'durationMinutes': 60,
  'status': status,
  'notes': null,
  'plannedArrival': eta,
  'plannedEnd': null,
  'legKm': 4.2,
};

final _stopsJson = <String, dynamic>{
  'date': '2026-10-01',
  'office': {
    'name': 'ServicePros HQ',
    'address': '100 Congress Ave, Austin, TX',
    'lat': 30.2672,
    'lng': -97.7431,
  },
  'technician': {
    'id': 7,
    'name': 'Ravi Kumar',
    'phone': '+15125550100',
    'email': 'ravi@servicepros.local',
    'skills': 'HVAC, Plumbing',
    'active': true,
    'hasLogin': true,
  },
  'routes': [
    {
      'routeId': 3,
      'routeName': 'North Route',
      'vehicleName': 'Van 1',
      'vehiclePlate': 'TX-1234',
      'technicians': [
        {'id': 7, 'name': 'Ravi Kumar'},
      ],
      'startTime': '08:00',
      'optimized': true,
      'stale': false,
      'totalKm': 42.5,
      'endTime': '16:30',
      'optimizedAt': '2026-09-30T18:00:00Z',
      'notifiedAt': null,
      'googleMapsUrls': ['https://www.google.com/maps/dir/a/b'],
      // Deliberately out of order: parsing sorts by `order`.
      'stops': [
        _stop(12, 2, 'DELAYED', lat: 30.40, lng: -97.70, eta: '10:15'),
        _stop(11, 1, 'COMPLETED', lat: 30.30, lng: -97.74, eta: '08:30'),
        _stop(13, 3, 'CONFIRMED', eta: '13:00'),
      ],
    },
  ],
};

void main() {
  group('TechStops.fromJson', () {
    final data = TechStops.fromJson(_stopsJson);

    test('parses office, technician and route', () {
      expect(data.date, '2026-10-01');
      expect(data.office.name, 'ServicePros HQ');
      expect(data.office.lat, 30.2672);
      expect(data.technician!.name, 'Ravi Kumar');
      expect(data.technician!.skills, 'HVAC, Plumbing');
      final r = data.routes.single;
      expect(r.routeName, 'North Route');
      expect(r.vehiclePlate, 'TX-1234');
      expect(r.technicians.single.name, 'Ravi Kumar');
      expect(r.totalKm, 42.5);
      expect(r.optimized, isTrue);
      expect(r.googleMapsUrls, hasLength(1));
    });

    test('sorts stops by order and parses fields', () {
      final stops = data.routes.single.stops;
      expect(stops.map((s) => s.order), [1, 2, 3]);
      final first = stops.first;
      expect(first.appointmentId, 11);
      expect(first.isCompleted, isTrue);
      expect(first.plannedArrival, '08:30');
      expect(first.legKm, 4.2);
      expect(stops[2].hasCoords, isFalse);
      expect(stops[2].canStartTravel, isTrue);
      expect(stops[1].isDelayed, isTrue);
    });

    test('derived counts', () {
      expect(data.stopCount, 3);
      expect(data.completedCount, 1);
      expect(data.firstOpenStop!.stop.appointmentId, 12);
      expect(data.findStop(13)!.route.routeId, 3);
      expect(data.findStop(999), isNull);
    });

    test('NavLeg distance from previous stop, minutes at 32 km/h', () {
      final leg = NavLeg.forStop(data, 12)!;
      expect(leg.km, closeTo(11.8, 0.3));
      expect(leg.minutes, (leg.km! / 32 * 60).round());
      // First stop measures from the depot.
      final first = NavLeg.forStop(data, 11)!;
      expect(first.km, closeTo(3.6, 0.3));
      // No coordinates → no distance.
      expect(NavLeg.forStop(data, 13)!.km, isNull);
    });

    test('handles empty / missing collections', () {
      final empty = TechStops.fromJson({
        'date': '2026-10-02',
        'office': {'name': 'HQ', 'address': '', 'lat': null, 'lng': null},
        'technician': null,
        'routes': [],
      });
      expect(empty.allStops, isEmpty);
      expect(empty.firstOpenStop, isNull);
      expect(empty.office.hasCoords, isFalse);
    });
  });

  test('CompleteResult.fromJson', () {
    final r = CompleteResult.fromJson({
      'appointment': _stop(11, 1, 'COMPLETED'),
    });
    expect(r.appointment.status, AppointmentStatus.completed);
  });

  test('AuthResponse.fromJson', () {
    final a = AuthResponse.fromJson({
      'accessToken': 'acc',
      'refreshToken': 'ref',
      'tokenType': 'Bearer',
      'expiresIn': 900,
      'user': {
        'id': 2,
        'name': 'Ravi Kumar',
        'email': 'ravi@servicepros.local',
        'role': 'TECHNICIAN',
        'technicianId': 7,
        'active': true,
      },
    });
    expect(a.expiresIn, 900);
    expect(a.user.isTechnician, isTrue);
    expect(a.user.technicianId, 7);
  });

  test('Page<Notification> parsing', () {
    final page = PageResult.fromJson({
      'content': [
        {
          'id': 1,
          'type': 'DELAY',
          'channel': 'SMS',
          'status': 'SIMULATED',
          'toName': 'Customer 12',
          'toAddress': '+15125550012',
          'message': 'Running 30 min late',
          'error': null,
          'appointmentId': 12,
          'createdAt': '2026-10-01T15:30:00Z',
        },
      ],
      'page': 0,
      'size': 20,
      'totalElements': 21,
      'totalPages': 2,
    }, AppNotification.fromJson);
    expect(page.content.single.type, 'DELAY');
    expect(page.content.single.appointmentId, 12);
    expect(page.isLast, isFalse);
    expect(notificationTypeInfo('DELAY').$1, '⏳');
    expect(notificationTypeInfo('ENROUTE').$1, '🚚');
    expect(notificationTypeInfo('REVIEW').$1, '⭐');
  });

  test('TechProfile.fromJson', () {
    final p = TechProfile.fromJson({
      'user': {
        'id': 2,
        'name': 'Ravi Kumar',
        'email': 'ravi@servicepros.local',
        'role': 'TECHNICIAN',
        'technicianId': 7,
        'active': true,
      },
      'technician': _stopsJson['technician'],
      'routes': [
        {
          'id': 3,
          'name': 'North Route',
          'vehicleName': 'Van 1',
          'vehiclePlate': 'TX-1234',
          'city': 'Katy',
        },
      ],
    });
    expect(p.technician!.phone, '+15125550100');
    expect(p.routes.single.city, 'Katy');
  });
}
