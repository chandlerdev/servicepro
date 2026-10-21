import 'dart:async';

import '../models/json_utils.dart';
import '../models/stop_models.dart';
import '../services/api_client.dart';

/// Technician stops and stop actions (`/api/tech/stops`, `/api/tech/appointments/**`).
class StopsRepository {
  StopsRepository(this._api);

  final ApiClient _api;
  final _changes = StreamController<void>.broadcast();

  /// Fires after a successful action changed a stop (On my way / Delay /
  /// Done) so other screens (e.g. Profile's "today" stats) can reload.
  Stream<void> get changes => _changes.stream;

  /// `GET /api/tech/stops?date=YYYY-MM-DD`
  Future<TechStops> fetchStops(DateTime date) async => TechStops.fromJson(
    jsonObject(
      await _api.get('/api/tech/stops', query: {'date': apiDate(date)}),
    ),
  );

  /// `POST /api/tech/appointments/{id}/on-my-way` → Stop
  Future<Stop> onMyWay(int appointmentId) async {
    final res = await _api.post(
      '/api/tech/appointments/$appointmentId/on-my-way',
    );
    _notifyChanged();
    return Stop.fromJson(jMap(res));
  }

  /// `POST /api/tech/appointments/{id}/delay {minutes, reason?}` → `{notified}`
  Future<int> reportDelay(
    int appointmentId,
    int minutes,
    String? reason,
  ) async {
    final res = await _api.post(
      '/api/tech/appointments/$appointmentId/delay',
      body: {
        'minutes': minutes,
        if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim(),
      },
    );
    _notifyChanged();
    return jInt(jMap(res)['notified']) ?? 0;
  }

  /// `POST /api/tech/appointments/{id}/complete {notes?}` → `{appointment}`
  Future<CompleteResult> complete(int appointmentId, String? notes) async {
    final res = await _api.post(
      '/api/tech/appointments/$appointmentId/complete',
      body: {
        if (notes != null && notes.trim().isNotEmpty) 'notes': notes.trim(),
      },
    );
    _notifyChanged();
    return CompleteResult.fromJson(jMap(res));
  }

  void _notifyChanged() {
    if (!_changes.isClosed) _changes.add(null);
  }

  void dispose() => _changes.close();

  /// Local date → API `YYYY-MM-DD`.
  static String apiDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';
}
