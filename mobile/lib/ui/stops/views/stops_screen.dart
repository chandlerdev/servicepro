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
import '../widgets/stop_card.dart';

class StopsScreen extends StatelessWidget {
  const StopsScreen({super.key});

  Future<void> _refresh(BuildContext context) async {
    final result = await context.read<StopsViewModel>().refresh();
    if (result case Err(:final error) when context.mounted) {
      showSnack(context, error.message, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<StopsViewModel>();
    final data = vm.data;

    Widget body;
    if (vm.isLoading) {
      body = const Center(child: CircularProgressIndicator());
    } else if (vm.error != null) {
      body = _scrollable(
        ErrorView(message: vm.error!.message, onRetry: vm.load),
      );
    } else if (data == null) {
      body = const Center(child: CircularProgressIndicator());
    } else {
      body = data.allStops.isEmpty
          ? _scrollable(
              const MessageView(
                icon: '🗓️',
                title: 'No stops for this date',
                subtitle:
                    'Your dispatcher allocates the schedule in Day Plan & Map.',
              ),
            )
          : _StopsList(data: data);
    }

    return Column(
      children: [
        const _DateBar(),
        Expanded(
          child: RefreshIndicator(
            color: AppColors.brand,
            onRefresh: () => _refresh(context),
            child: body,
          ),
        ),
      ],
    );
  }

  Widget _scrollable(Widget child) => ListView(
    physics: const AlwaysScrollableScrollPhysics(),
    children: [child],
  );
}

class _DateBar extends StatelessWidget {
  const _DateBar();

  Future<void> _pick(BuildContext context, DateTime current) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(now.year - 1),
      lastDate: DateTime(now.year + 1, 12, 31),
      helpText: 'Show stops for',
    );
    if (picked != null && context.mounted) {
      context.read<HomeViewModel>().setDate(picked);
    }
  }

  @override
  Widget build(BuildContext context) {
    final date = context.select<HomeViewModel, DateTime>(
      (h) => h.selectedDate,
    );
    final home = context.read<HomeViewModel>();
    final isToday = date == Fmt.dateOnly(DateTime.now());
    return Material(
      color: Colors.white,
      elevation: 0.5,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
        child: Row(
          children: [
            IconButton(
              tooltip: 'Previous day',
              icon: const Icon(Icons.chevron_left),
              onPressed: home.previousDay,
            ),
            Expanded(
              child: TextButton.icon(
                onPressed: () => _pick(context, date),
                icon: const Icon(Icons.calendar_today_outlined, size: 18),
                label: Text(
                  Fmt.relativeDate(date) == Fmt.niceDate(date)
                      ? Fmt.niceDate(date)
                      : '${Fmt.relativeDate(date)} · ${Fmt.niceDate(date)}',
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            ),
            if (!isToday)
              TextButton(onPressed: home.today, child: const Text('Today')),
            IconButton(
              tooltip: 'Next day',
              icon: const Icon(Icons.chevron_right),
              onPressed: home.nextDay,
            ),
          ],
        ),
      ),
    );
  }
}

class _StopsList extends StatelessWidget {
  const _StopsList({required this.data});

  final TechStops data;

  @override
  Widget build(BuildContext context) {
    final first = data.firstOpenStop;
    final children = <Widget>[
      if (first != null) _ScheduleBanner(first: first),
      for (final route in data.routes) ...[
        _RouteHeader(route: route, showName: data.routes.length > 1),
        for (final s in route.stops)
          Padding(
            padding: const EdgeInsets.only(bottom: 9),
            child: StopCard(key: ValueKey(s.appointmentId), stop: s),
          ),
      ],
      const SizedBox(height: 8),
      const Text(
        '“Delay” texts every remaining customer on your route a new ETA. '
        '“Done” closes the job and texts the customer Google & Facebook review links.',
        textAlign: TextAlign.center,
        style: TextStyle(fontSize: 12, color: AppColors.muted),
      ),
      const SizedBox(height: 16),
    ];
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
      children: children,
    );
  }
}

class _ScheduleBanner extends StatelessWidget {
  const _ScheduleBanner({required this.first});

  final RouteStop first;

  @override
  Widget build(BuildContext context) {
    final optimized = first.route.optimized && !first.route.stale;
    final eta = first.stop.plannedArrival != null
        ? Fmt.hm(first.stop.plannedArrival)
        : 'TBD';
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(13),
        boxShadow: const [
          BoxShadow(
            color: Color(0x1F142332),
            blurRadius: 10,
            offset: Offset(0, 3),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('🔔', style: TextStyle(fontSize: 16)),
          const SizedBox(width: 9),
          Expanded(
            child: Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: optimized ? 'Schedule ready\n' : 'Schedule pending\n',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  TextSpan(
                    text: optimized
                        ? 'Your route is optimized — first stop ${first.stop.customerName} at $eta.'
                        : 'Route not optimized yet — first stop ${first.stop.customerName} at $eta.',
                  ),
                ],
              ),
              style: const TextStyle(fontSize: 13, height: 1.35),
            ),
          ),
        ],
      ),
    );
  }
}

class _RouteHeader extends StatelessWidget {
  const _RouteHeader({required this.route, required this.showName});

  final RoutePlan route;
  final bool showName;

  @override
  Widget build(BuildContext context) {
    final parts = <String>[
      if (showName) route.routeName,
      if (route.vehicleName != null)
        '🚐 ${route.vehicleName}${route.vehiclePlate != null ? ' (${route.vehiclePlate})' : ''}',
      if (route.startTime != null) 'Start ${Fmt.hm(route.startTime)}',
      if (route.endTime != null) 'End ${Fmt.hm(route.endTime)}',
      if (route.totalKm != null) Fmt.km(route.totalKm!),
    ];
    if (parts.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 2, 4, 8),
      child: Text(
        parts.join(' · '),
        style: const TextStyle(
          fontSize: 12,
          color: AppColors.muted,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
