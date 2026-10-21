import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../../config/app_config.dart';

/// Error surfaced to the UI. [message] is the API's `message` field when
/// available, otherwise a friendly description of the transport failure.
class ApiException implements Exception {
  ApiException(this.message, {this.statusCode, this.fieldErrors = const {}});

  final String message;
  final int? statusCode;
  final Map<String, String> fieldErrors;

  bool get isUnauthorized => statusCode == 401;

  /// Message with field errors appended (validation failures).
  String get detailed {
    if (fieldErrors.isEmpty) return message;
    final fields = fieldErrors.entries.map((e) => '${e.key}: ${e.value}');
    return '$message\n${fields.join('\n')}';
  }

  /// Builds an exception from a non-2xx response. The backend error body is
  /// `{ status, error, message, path, timestamp, fieldErrors? }`.
  factory ApiException.fromResponse(int statusCode, String body) {
    String? msg;
    final fields = <String, String>{};
    try {
      final data = body.isEmpty ? null : jsonDecode(body);
      if (data is Map) {
        final m = data['message'];
        if (m is String && m.trim().isNotEmpty) msg = m;
        final fe = data['fieldErrors'];
        if (fe is Map) fe.forEach((k, v) => fields['$k'] = '$v');
      }
    } on FormatException {
      // Not JSON (e.g. proxy error page): fall back to a generic message.
    }
    msg ??= switch (statusCode) {
      400 => 'Invalid request.',
      401 => 'Your session has expired. Please sign in again.',
      403 => 'You are not allowed to do that.',
      404 => 'Not found.',
      409 => 'Conflict — the item was changed elsewhere.',
      429 => 'Too many attempts. Please wait a minute and try again.',
      final c when c >= 500 => 'Server error ($c). Please try again.',
      _ => 'Request failed ($statusCode).',
    };
    return ApiException(msg, statusCode: statusCode, fieldErrors: fields);
  }

  /// The request did not complete within the client timeout.
  factory ApiException.timeout() => ApiException(
    'The server took too long to respond. Check your connection.',
  );

  /// The server could not be reached (DNS, refused connection, no network, TLS…).
  /// Debug builds append the underlying error so the cause is visible on screen.
  factory ApiException.network([Object? cause]) => ApiException(
    "Can't reach the server (${AppConfig.apiBaseUrl}). Check your connection."
    '${kDebugMode && cause != null ? '\n\n[debug] $cause' : ''}',
  );

  /// A 2xx response whose body was not the JSON we expected.
  factory ApiException.badResponse() =>
      ApiException('Unexpected response from the server.');

  static ApiException from(Object error) {
    if (error is ApiException) return error;
    return ApiException(error.toString());
  }

  @override
  String toString() => message;
}
