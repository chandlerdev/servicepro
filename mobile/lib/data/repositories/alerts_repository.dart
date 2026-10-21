import '../models/app_notification.dart';
import '../models/page_result.dart';
import '../services/api_client.dart';

/// Notifications sent to customers on the technician's routes.
class AlertsRepository {
  AlertsRepository(this._api);

  final ApiClient _api;

  /// `GET /api/tech/notifications?page=&size=` → `Page<Notification>`
  Future<PageResult<AppNotification>> fetch({
    int page = 0,
    int size = 20,
  }) async => PageResult.fromJson(
    jsonObject(
      await _api.get(
        '/api/tech/notifications',
        query: {'page': page, 'size': size},
      ),
    ),
    AppNotification.fromJson,
  );
}
