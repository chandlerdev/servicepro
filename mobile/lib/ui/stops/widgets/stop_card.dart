import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../config/app_theme.dart';
import '../../../data/models/stop_models.dart';
import '../../../utils/result.dart';
import '../../core/utils/formatters.dart';
import '../../core/utils/ui.dart';
import '../../core/widgets/common.dart';
import '../../home/view_model/home_view_model.dart';
import '../view_model/stops_view_model.dart';
import 'stop_dialogs.dart';

/// One stop with its actions. Busy state lives in [StopsViewModel] (per
/// appointment), feedback snackbars are shown here.
class StopCard extends StatelessWidget {
  const StopCard({super.key, required this.stop});

  final Stop stop;

  void _report<T>(
    BuildContext context,
    Result<T> result,
    String Function(T value) success,
  ) {
    if (!context.mounted) return;
    switch (result) {
      case Ok(:final value):
        showSnack(context, success(value));
      case Err(:final error):
        showSnack(context, error.detailed, error: true);
    }
  }

  Future<void> _onMyWay(BuildContext context) async {
    final r = await context.read<StopsViewModel>().onMyWay(stop.appointmentId);
    if (!context.mounted) return;
    _report(
      context,
      r,
      (_) => '🚚 ${stop.customerName} was texted that you are on the way',
    );
  }

  Future<void> _delay(BuildContext context) async {
    final vm = context.read<StopsViewModel>();
    final input = await showDelayDialog(context, stop);
    if (input == null || !context.mounted) return;
    final r = await vm.delay(stop.appointmentId, input.minutes, input.reason);
    if (!context.mounted) return;
    _report(
      context,
      r,
      (n) => '⏳ Delay logged — ${Fmt.plural(n, 'customer')} notified',
    );
  }

  Future<void> _complete(BuildContext context) async {
    final vm = context.read<StopsViewModel>();
    final input = await showCompleteDialog(context, stop);
    if (input == null || !context.mounted) return;
    final r = await vm.complete(stop.appointmentId, input.notes);
    if (!context.mounted) return;
    _report(context, r, (_) => '✔ Job completed — review request sent');
  }

  @override
  Widget build(BuildContext context) {
    final running = context.select<StopsViewModel, StopAction?>(
      (vm) => vm.busyAction(stop.appointmentId),
    );
    final busy = running != null;
    final muted = stop.isCompleted || stop.isCancelled;
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                StopNumber(stop.order, status: stop.status),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        stop.customerName,
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: muted ? AppColors.muted : AppColors.ink,
                          decoration: stop.isCancelled
                              ? TextDecoration.lineThrough
                              : null,
                        ),
                      ),
                      Text(
                        stop.durationMinutes > 0
                            ? '${stop.taskName} · ${stop.durationMinutes} min'
                            : stop.taskName,
                        style: const TextStyle(
                          color: AppColors.muted,
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 6),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    StatusChip(stop.status),
                    const SizedBox(height: 4),
                    Text(
                      Fmt.hm(stop.plannedArrival),
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: AppColors.brandDark,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.only(left: 38, top: 6),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '📍 ${stop.address}',
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppColors.muted,
                    ),
                  ),
                  if ((stop.notes ?? '').trim().isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                        '📝 ${stop.notes!.trim()}',
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.muted,
                        ),
                      ),
                    ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: [
                      _SmallButton(
                        label: '🧭 Navigate',
                        onPressed: busy || !stop.hasCoords
                            ? null
                            : () => context.read<HomeViewModel>().navigateToStop(
                                stop.appointmentId,
                              ),
                      ),
                      if (stop.canStartTravel)
                        _SmallButton(
                          label: '🚚 On my way',
                          kind: _Kind.primary,
                          loading: running == StopAction.onMyWay,
                          onPressed: busy ? null : () => _onMyWay(context),
                        ),
                      if (stop.isOpen) ...[
                        _SmallButton(
                          label: '⏳ Delay',
                          kind: _Kind.warn,
                          loading: running == StopAction.delay,
                          onPressed: busy ? null : () => _delay(context),
                        ),
                        _SmallButton(
                          label: '✔ Done',
                          kind: _Kind.ok,
                          loading: running == StopAction.complete,
                          onPressed: busy ? null : () => _complete(context),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

enum _Kind { secondary, primary, warn, ok }

class _SmallButton extends StatelessWidget {
  const _SmallButton({
    required this.label,
    required this.onPressed,
    this.kind = _Kind.secondary,
    this.loading = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final _Kind kind;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    final (Color bg, Color fg, Color border) = switch (kind) {
      _Kind.primary => (AppColors.brand, Colors.white, AppColors.brand),
      _Kind.warn => (AppColors.warnBg, AppColors.warn, AppColors.warnBorder),
      _Kind.ok => (AppColors.okBg, AppColors.ok, AppColors.okBorder),
      _Kind.secondary => (
        const Color(0xFFF0F4F8),
        AppColors.ink,
        AppColors.line,
      ),
    };
    return TextButton(
      onPressed: onPressed,
      style: TextButton.styleFrom(
        backgroundColor: bg,
        foregroundColor: fg,
        disabledForegroundColor: fg.withValues(alpha: 0.45),
        disabledBackgroundColor: bg.withValues(alpha: 0.6),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        minimumSize: const Size(0, 34),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: BorderSide(color: border),
        ),
      ),
      child: loading
          ? Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                ButtonSpinner(color: fg),
                const SizedBox(width: 6),
                Text(label),
              ],
            )
          : Text(label),
    );
  }
}
