import 'package:intl/intl.dart';

/// Display helpers shared by all features.
class Fmt {
  Fmt._();

  static final DateFormat _apiDate = DateFormat('yyyy-MM-dd');
  static final DateFormat _niceDate = DateFormat('EEE, MMM d');
  static final DateFormat _longDate = DateFormat('EEEE, MMMM d, y');
  static final DateFormat _time = DateFormat('h:mm a');
  static final DateFormat _dayMonth = DateFormat('MMM d');

  /// `"08:30"` / `"14:05"` (API 24h `HH:mm`, seconds tolerated) → `"8:30 AM"` /
  /// `"2:05 PM"`. Returns [fallback] for null/blank/invalid input.
  static String hm(String? value, {String fallback = '—'}) {
    if (value == null) return fallback;
    final parts = value.trim().split(':');
    if (parts.length < 2) return fallback;
    final h = int.tryParse(parts[0]);
    final m = int.tryParse(parts[1]);
    if (h == null || m == null || h < 0 || h > 23 || m < 0 || m > 59) {
      return fallback;
    }
    final suffix = h < 12 ? 'AM' : 'PM';
    final h12 = h % 12 == 0 ? 12 : h % 12;
    return '$h12:${m.toString().padLeft(2, '0')} $suffix';
  }

  /// Date → API `YYYY-MM-DD`.
  static String apiDate(DateTime d) => _apiDate.format(d);

  /// API `YYYY-MM-DD` → local [DateTime] (midnight) or null.
  static DateTime? parseApiDate(String? s) {
    if (s == null || s.isEmpty) return null;
    try {
      return _apiDate.parseStrict(s);
    } catch (_) {
      return null;
    }
  }

  /// `Thu, Oct 1`
  static String niceDate(DateTime d) => _niceDate.format(d);

  /// `Thursday, October 1, 2026`
  static String longDate(DateTime d) => _longDate.format(d);

  /// "Today", "Tomorrow", "Yesterday" or `Thu, Oct 1`.
  static String relativeDate(DateTime d, {DateTime? now}) {
    final today = dateOnly(now ?? DateTime.now());
    final diff = dateOnly(d).difference(today).inDays;
    return switch (diff) {
      0 => 'Today',
      1 => 'Tomorrow',
      -1 => 'Yesterday',
      _ => niceDate(d),
    };
  }

  /// ISO-8601 timestamp → local "8:30 AM" if today, else "Oct 1, 8:30 AM".
  static String timestamp(String? iso, {DateTime? now}) {
    if (iso == null) return '';
    final t = DateTime.tryParse(iso);
    if (t == null) return iso;
    final local = t.toLocal();
    final n = now ?? DateTime.now();
    if (dateOnly(local) == dateOnly(n)) return _time.format(local);
    return '${_dayMonth.format(local)}, ${_time.format(local)}';
  }

  static DateTime dateOnly(DateTime d) => DateTime(d.year, d.month, d.day);

  /// `EN_ROUTE` → "En Route".
  static String enumLabel(String? value) {
    if (value == null || value.isEmpty) return '';
    return value
        .split('_')
        .where((w) => w.isNotEmpty)
        .map((w) => w[0].toUpperCase() + w.substring(1).toLowerCase())
        .join(' ');
  }

  /// "Ravi Kumar" → "RK".
  static String initials(String? name) {
    final words = (name ?? '')
        .trim()
        .split(RegExp(r'\s+'))
        .where((w) => w.isNotEmpty);
    final s = words.map((w) => w[0].toUpperCase()).take(2).join();
    return s.isEmpty ? '?' : s;
  }

  static String plural(int n, String word, [String? pluralWord]) =>
      '$n ${n == 1 ? word : (pluralWord ?? '${word}s')}';

  static String km(double km) => '${km.toStringAsFixed(1)} km';
}
