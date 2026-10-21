import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../data/models/stop_models.dart';
import '../../alerts/views/alerts_screen.dart';
import '../../core/utils/formatters.dart';
import '../../map/views/map_screen.dart';
import '../../profile/views/profile_screen.dart';
import '../../stops/view_model/stops_view_model.dart';
import '../../stops/views/stops_screen.dart';
import '../view_model/home_view_model.dart';

/// Bottom-navigation shell: My Stops / Map / Alerts / Profile.
///
/// Drives the stop list's 60 s auto-refresh while the app is foregrounded.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  late final StopsViewModel _stops = context.read<StopsViewModel>();
  late final AppLifecycleListener _lifecycle;

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(
      onResume: _stops.onResume,
      onHide: _stops.stopAutoRefresh,
    );
    _stops.startAutoRefresh();
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    _stops.stopAutoRefresh();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tab = context.select<HomeViewModel, HomeTab>((h) => h.tab);
    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 64,
        titleSpacing: 16,
        title: const _Header(),
      ),
      body: IndexedStack(
        index: tab.index,
        children: const [
          StopsScreen(),
          MapScreen(),
          AlertsScreen(),
          ProfileScreen(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab.index,
        onDestinationSelected: (i) =>
            context.read<HomeViewModel>().selectTab(HomeTab.values[i]),
        destinations: const [
          NavigationDestination(
            icon: Text('🗓️', style: TextStyle(fontSize: 20)),
            label: 'My Stops',
          ),
          NavigationDestination(
            icon: Text('🗺️', style: TextStyle(fontSize: 20)),
            label: 'Map',
          ),
          NavigationDestination(
            icon: Text('📨', style: TextStyle(fontSize: 20)),
            label: 'Alerts',
          ),
          NavigationDestination(
            icon: Text('👤', style: TextStyle(fontSize: 20)),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}

/// "⚡ ServProWare" + "Name · date · N stops · route names".
class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    final date = context.select<HomeViewModel, DateTime>(
      (h) => h.selectedDate,
    );
    final userName = context.select<HomeViewModel, String?>((h) => h.userName);
    final data = context.select<StopsViewModel, TechStops?>((s) => s.data);
    final name = data?.technician?.name ?? userName ?? '';
    final parts = <String>[
      if (name.isNotEmpty) name,
      Fmt.niceDate(date),
      if (data != null) Fmt.plural(data.stopCount, 'stop'),
      if (data != null && data.routes.isNotEmpty)
        data.routes.map((r) => r.routeName).join(', '),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        const Text(
          '⚡ ServProWare',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
        ),
        Text(
          parts.join(' · '),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 12.5, color: Colors.white70),
        ),
      ],
    );
  }
}
