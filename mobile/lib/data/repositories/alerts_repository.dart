import '../models/app_notification.dart';
import '../models/page_result.dart';
import '../services/api_client.dart';

/// Notifications sent to customers on the technician's routes.
class AlertsRepository {
  AlertsRepository(this._api);

  final ApiClient _api;

  /// `GET /api/tech/notifications?from=&to=&page=&size=` → `Page<Notification>`
  ///
  /// [from] / [to] are YYYY-MM-DD (inclusive). Left out, the server returns
  /// yesterday and today.
  Future<PageResult<AppNotification>> fetch({
    int page = 0,
    int size = 20,
    String? from,
    String? to,
  }) async => PageResult.fromJson(
    jsonObject(
      await _api.get(
        '/api/tech/notifications',
        query: {
          'page': page,
          'size': size,
          if (from != null) 'from': from,
          if (to != null) 'to': to,
        },
      ),
    ),
    AppNotification.fromJson,
  );
}
