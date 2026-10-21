import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../config/app_theme.dart';
import '../../../data/models/app_notification.dart';
import '../../../utils/result.dart';
import '../../core/utils/formatters.dart';
import '../../core/utils/ui.dart';
import '../../core/widgets/common.dart';
import '../view_model/alerts_view_model.dart';

class AlertsScreen extends StatefulWidget {
  const AlertsScreen({super.key});

  @override
  State<AlertsScreen> createState() => _AlertsScreenState();
}

class _AlertsScreenState extends State<AlertsScreen> {
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!_scroll.hasClients) return;
    final pos = _scroll.position;
    final vm = context.read<AlertsViewModel>();
    if (!vm.hasData || vm.loadMoreError != null) return; // footer offers retry
    if (pos.pixels >= pos.maxScrollExtent - 300) vm.loadMore();
  }

  Future<void> _refresh() async {
    final result = await context.read<AlertsViewModel>().refresh();
    if (result case Err(:final error) when mounted) {
      showSnack(context, error.message, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<AlertsViewModel>();

    Widget child;
    if (!vm.hasData && vm.isLoading) {
      child = const Center(child: CircularProgressIndicator());
    } else if (vm.error != null && !vm.hasData) {
      child = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: [ErrorView(message: vm.error!.message, onRetry: vm.load)],
      );
    } else if (vm.items.isEmpty) {
      child = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [
          MessageView(
            icon: '📨',
            title: 'No alerts yet',
            subtitle: 'Texts sent to customers on your routes will appear here.',
          ),
        ],
      );
    } else {
      // If the first page doesn't fill the screen, fetch more.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted &&
            vm.hasMore &&
            vm.loadMoreError == null &&
            _scroll.hasClients &&
            _scroll.position.maxScrollExtent <= 0) {
          vm.loadMore();
        }
      });
      final items = vm.items;
      child = ListView.separated(
        controller: _scroll,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 16),
        itemCount: items.length + 1,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (context, i) {
          if (i == items.length) return _Footer(vm: vm);
          return _AlertTile(n: items[i]);
        },
      );
    }

    return RefreshIndicator(
      color: AppColors.brand,
      onRefresh: _refresh,
      child: child,
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer({required this.vm});

  final AlertsViewModel vm;

  @override
  Widget build(BuildContext context) {
    if (vm.loadingMore) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (vm.loadMoreError != null) {
      return Center(
        child: TextButton.icon(
          onPressed: vm.loadMore,
          icon: const Icon(Icons.refresh),
          label: const Text("Couldn't load more — retry"),
        ),
      );
    }
    if (!vm.hasMore) {
      return const Padding(
        padding: EdgeInsets.all(12),
        child: Center(
          child: Text(
            'No more alerts',
            style: TextStyle(color: AppColors.muted, fontSize: 12),
          ),
        ),
      );
    }
    return const SizedBox(height: 40);
  }
}

class _AlertTile extends StatelessWidget {
  const _AlertTile({required this.n});

  final AppNotification n;

  @override
  Widget build(BuildContext context) {
    final (icon, label) = notificationTypeInfo(n.type);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: AppColors.brandLight,
                borderRadius: BorderRadius.circular(9),
              ),
              child: Text(icon, style: const TextStyle(fontSize: 17)),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(
                          text: label,
                          style: const TextStyle(
                            fontWeight: FontWeight.w700,
                            color: AppColors.ink,
                          ),
                        ),
                        TextSpan(
                          text:
                              ' · ${Fmt.timestamp(n.createdAt)} · '
                              '${n.channel} to ${n.toName}',
                        ),
                      ],
                    ),
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.muted,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(n.message, style: const TextStyle(fontSize: 13.5)),
                  if (n.failed || n.status == 'SIMULATED')
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        n.failed
                            ? '⚠ Failed${n.error != null ? ': ${n.error}' : ''}'
                            : 'Simulated (SMS provider not configured)',
                        style: TextStyle(
                          fontSize: 11.5,
                          color: n.failed ? AppColors.danger : AppColors.muted,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
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
