import 'json_utils.dart';

/// ```
/// Notification = { id, type, channel, status, toName, toAddress, message,
///   error: string|null, appointmentId: number|null, createdAt }
/// ```
class AppNotification {
  const AppNotification({
    required this.id,
    required this.type,
    required this.channel,
    required this.status,
    required this.toName,
    required this.toAddress,
    required this.message,
    required this.error,
    required this.appointmentId,
    required this.createdAt,
  });

  final int id;
  final String type;
  final String channel;
  final String status;
  final String toName;
  final String toAddress;
  final String message;
  final String? error;
  final int? appointmentId;
  final String? createdAt;

  bool get failed => status == 'FAILED';

  factory AppNotification.fromJson(Map<String, dynamic> json) =>
      AppNotification(
        id: jInt(json['id']) ?? 0,
        type: jString(json['type']) ?? '',
        channel: jString(json['channel']) ?? 'SMS',
        status: jString(json['status']) ?? 'SENT',
        toName: jString(json['toName']) ?? '',
        toAddress: jString(json['toAddress']) ?? '',
        message: jString(json['message']) ?? '',
        error: jString(json['error']),
        appointmentId: jInt(json['appointmentId']),
        createdAt: jString(json['createdAt']),
      );
}

/// Icon + label per NotificationType (matches the web UI).
(String icon, String label) notificationTypeInfo(String type) => switch (type) {
  'BOOKING' => ('📅', 'Booking confirmation'),
  'SCHEDULE' => ('🕗', 'Day-before schedule'),
  'UPDATE' => ('🔁', 'Schedule update'),
  'DELAY' => ('⏳', 'Delay alert'),
  'REVIEW' => ('⭐', 'Review request'),
  'ENROUTE' => ('🚚', 'On the way'),
  _ => ('📨', type),
};
