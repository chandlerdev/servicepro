import 'package:flutter_test/flutter_test.dart';
import 'package:servicepros_tech/ui/core/utils/formatters.dart';
import 'package:servicepros_tech/ui/core/utils/geo.dart';

void main() {
  group('Fmt.hm', () {
    test('converts 24h HH:mm to 12h with AM/PM', () {
      expect(Fmt.hm('08:30'), '8:30 AM');
      expect(Fmt.hm('00:05'), '12:05 AM');
      expect(Fmt.hm('12:00'), '12:00 PM');
      expect(Fmt.hm('14:05'), '2:05 PM');
      expect(Fmt.hm('23:59'), '11:59 PM');
    });

    test('tolerates seconds', () {
      expect(Fmt.hm('09:15:00'), '9:15 AM');
    });

    test('returns fallback for null / invalid', () {
      expect(Fmt.hm(null), '—');
      expect(Fmt.hm(''), '—');
      expect(Fmt.hm('25:00'), '—');
      expect(Fmt.hm('abc'), '—');
      expect(Fmt.hm(null, fallback: 'TBD'), 'TBD');
    });
  });

  group('dates', () {
    test('apiDate / parseApiDate round-trip', () {
      final d = DateTime(2026, 10, 1);
      expect(Fmt.apiDate(d), '2026-10-01');
      expect(Fmt.parseApiDate('2026-10-01'), d);
      expect(Fmt.parseApiDate('nope'), isNull);
    });

    test('relativeDate', () {
      final now = DateTime(2026, 10, 1, 15, 30);
      expect(Fmt.relativeDate(DateTime(2026, 10, 1), now: now), 'Today');
      expect(Fmt.relativeDate(DateTime(2026, 10, 2), now: now), 'Tomorrow');
      expect(Fmt.relativeDate(DateTime(2026, 9, 30), now: now), 'Yesterday');
      expect(Fmt.relativeDate(DateTime(2026, 10, 5), now: now), 'Mon, Oct 5');
    });
  });

  group('labels', () {
    test('enumLabel', () {
      expect(Fmt.enumLabel('EN_ROUTE'), 'En Route');
      expect(Fmt.enumLabel('COMPLETED'), 'Completed');
    });

    test('initials', () {
      expect(Fmt.initials('Ravi Kumar'), 'RK');
      expect(Fmt.initials('dana'), 'D');
      expect(Fmt.initials('  '), '?');
    });

    test('plural', () {
      expect(Fmt.plural(1, 'stop'), '1 stop');
      expect(Fmt.plural(3, 'stop'), '3 stops');
    });
  });

  group('geo', () {
    test('haversine Austin → Round Rock ≈ 27.5 km', () {
      final km = haversineKm(30.2672, -97.7431, 30.5083, -97.6789);
      expect(km, closeTo(27.5, 1.5));
    });

    test('driveMinutes at 32 km/h, minimum 2', () {
      expect(driveMinutes(16), 30);
      expect(driveMinutes(0.1), 2);
    });

    test('google maps hand-off url', () {
      expect(
        googleMapsDirections(30.1, -97.2).toString(),
        'https://www.google.com/maps/dir/?api=1&destination=30.1,-97.2&travelmode=driving',
      );
    });
  });
}
