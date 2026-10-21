import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../config/app_theme.dart';
import '../../../data/models/stop_models.dart';

typedef DelayInput = ({int minutes, String? reason});

/// Asks for delay minutes (5–480) and an optional reason.
Future<DelayInput?> showDelayDialog(BuildContext context, Stop stop) {
  return showDialog<DelayInput>(
    context: context,
    builder: (_) => _DelayDialog(stop: stop),
  );
}

class _DelayDialog extends StatefulWidget {
  const _DelayDialog({required this.stop});

  final Stop stop;

  @override
  State<_DelayDialog> createState() => _DelayDialogState();
}

class _DelayDialogState extends State<_DelayDialog> {
  final _formKey = GlobalKey<FormState>();
  final _minutes = TextEditingController(text: '30');
  final _reason = TextEditingController();

  static const _presets = [15, 30, 45, 60];

  @override
  void dispose() {
    _minutes.dispose();
    _reason.dispose();
    super.dispose();
  }

  void _submit() {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    Navigator.of(context).pop((
      minutes: int.parse(_minutes.text.trim()),
      reason: _reason.text.trim().isEmpty ? null : _reason.text.trim(),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('⏳ Report delay'),
      content: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'How late are you running for ${widget.stop.customerName}? '
                'This stop and every later open stop get a new ETA by SMS.',
                style: const TextStyle(color: AppColors.muted),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 6,
                children: [
                  for (final m in _presets)
                    ActionChip(
                      label: Text('$m min'),
                      onPressed: () => setState(() => _minutes.text = '$m'),
                    ),
                ],
              ),
              const SizedBox(height: 10),
              TextFormField(
                controller: _minutes,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: const InputDecoration(
                  labelText: 'Delay (minutes)',
                  helperText: '5 – 480',
                ),
                validator: (v) {
                  final n = int.tryParse(v?.trim() ?? '');
                  if (n == null) return 'Enter minutes';
                  if (n < 5 || n > 480) return 'Must be between 5 and 480';
                  return null;
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _reason,
                maxLength: 200,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Reason (optional)',
                  hintText: 'Traffic on I-35',
                ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(onPressed: _submit, child: const Text('Notify customers')),
      ],
    );
  }
}

/// Confirms completion; returns `(notes: ...)` or null if cancelled.
Future<({String? notes})?> showCompleteDialog(BuildContext context, Stop stop) {
  return showDialog<({String? notes})>(
    context: context,
    builder: (_) => _CompleteDialog(stop: stop),
  );
}

class _CompleteDialog extends StatefulWidget {
  const _CompleteDialog({required this.stop});

  final Stop stop;

  @override
  State<_CompleteDialog> createState() => _CompleteDialogState();
}

class _CompleteDialogState extends State<_CompleteDialog> {
  final _notes = TextEditingController();

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.stop;
    return AlertDialog(
      title: const Text('✔ Complete job'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text.rich(
              TextSpan(
                children: [
                  const TextSpan(text: 'Mark '),
                  TextSpan(
                    text: s.taskName,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const TextSpan(text: ' for '),
                  TextSpan(
                    text: s.customerName,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const TextSpan(text: ' as completed?'),
                ],
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'The customer receives a thank-you SMS with Google and Facebook review links.',
              style: TextStyle(color: AppColors.muted, fontSize: 13),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _notes,
              minLines: 2,
              maxLines: 4,
              maxLength: 1000,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Completion notes (optional)',
                hintText: 'Replaced filter, tested system',
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: AppColors.ok),
          onPressed: () {
            final n = _notes.text.trim();
            Navigator.of(context).pop((notes: n.isEmpty ? null : n));
          },
          child: const Text('Complete & send'),
        ),
      ],
    );
  }
}
