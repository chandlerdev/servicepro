import 'package:flutter/material.dart';

import '../../../config/app_theme.dart';
import '../utils/formatters.dart';

/// Numbered stop circle: brand by default, grey when done, red when delayed.
class StopNumber extends StatelessWidget {
  const StopNumber(
    this.number, {
    super.key,
    required this.status,
    this.size = 28,
  });

  final int number;
  final String status;
  final double size;

  static Color colorFor(String status) => switch (status) {
    'COMPLETED' || 'CANCELLED' => AppColors.doneStop,
    'DELAYED' => AppColors.danger,
    _ => AppColors.brand,
  };

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: colorFor(status),
        shape: BoxShape.circle,
      ),
      child: Text(
        '$number',
        style: TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w700,
          fontSize: size * 0.45,
        ),
      ),
    );
  }
}

class StatusChip extends StatelessWidget {
  const StatusChip(this.status, {super.key});

  final String status;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = statusColors(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        Fmt.enumLabel(status),
        style: TextStyle(color: fg, fontSize: 11, fontWeight: FontWeight.w700),
      ),
    );
  }
}

/// Full-height message usable inside a scrollable (so pull-to-refresh works).
class MessageView extends StatelessWidget {
  const MessageView({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.action,
  });

  final String icon;
  final String title;
  final String? subtitle;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 80, 32, 32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(icon, style: const TextStyle(fontSize: 40)),
          const SizedBox(height: 12),
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 6),
            Text(
              subtitle!,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted),
            ),
          ],
          if (action != null) ...[const SizedBox(height: 16), action!],
        ],
      ),
    );
  }
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => MessageView(
    icon: '⚠️',
    title: "Couldn't load",
    subtitle: message,
    action: FilledButton.icon(
      onPressed: onRetry,
      icon: const Icon(Icons.refresh),
      label: const Text('Retry'),
    ),
  );
}

/// Small spinner for use inside buttons.
class ButtonSpinner extends StatelessWidget {
  const ButtonSpinner({super.key, this.color});

  final Color? color;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 14,
    height: 14,
    child: CircularProgressIndicator(strokeWidth: 2, color: color),
  );
}
