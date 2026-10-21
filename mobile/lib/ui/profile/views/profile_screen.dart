import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../config/app_config.dart';
import '../../../config/app_theme.dart';
import '../../core/utils/formatters.dart';
import '../view_model/profile_view_model.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  Future<void> _signOut(BuildContext context) async {
    final vm = context.read<ProfileViewModel>();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Sign out?'),
        content: const Text(
          'You will need to sign in again to see your stops.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.danger),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Sign out'),
          ),
        ],
      ),
    );
    if (ok == true) await vm.signOut();
  }

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<ProfileViewModel>();
    final authUser = vm.authUser;

    final profile = vm.profile;
    final tech = profile?.technician;
    final name = tech?.name ?? profile?.user.name ?? authUser?.name ?? '';
    final today = vm.today;

    // Routes / vehicles: today's plans first, else assigned routes.
    final routeNames = today != null && today.routes.isNotEmpty
        ? today.routes.map((r) => r.routeName).toList()
        : (profile?.routes.map((r) => r.name).toList() ?? const <String>[]);
    String vehicle(String? name, String? plate) =>
        [?name, if (plate != null) '($plate)'].join(' ');
    final vehicles = today != null && today.routes.isNotEmpty
        ? today.routes.map((r) => vehicle(r.vehicleName, r.vehiclePlate))
        : (profile?.routes.map((r) => vehicle(r.vehicleName, r.vehiclePlate)) ??
              const <String>[]);
    final vehicleText = vehicles.where((v) => v.isNotEmpty).toSet().join(', ');

    return RefreshIndicator(
      color: AppColors.brand,
      onRefresh: vm.load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          const SizedBox(height: 12),
          Center(
            child: CircleAvatar(
              radius: 34,
              backgroundColor: AppColors.brand,
              child: Text(
                Fmt.initials(name),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ),
          const SizedBox(height: 10),
          Text(
            name.isEmpty ? '—' : name,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
          ),
          if (tech != null)
            Text(
              [
                if (tech.skills.isNotEmpty) tech.skills,
                if (tech.phone.isNotEmpty) tech.phone,
              ].join(' · '),
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted),
            ),
          if ((tech?.email ?? profile?.user.email ?? authUser?.email) != null)
            Text(
              tech?.email ?? profile?.user.email ?? authUser!.email,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted, fontSize: 12.5),
            ),
          if (vm.profileError != null && profile == null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                vm.profileError!.message,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.danger),
              ),
            ),
          const SizedBox(height: 18),
          _InfoCard(
            title: 'TODAY · ${Fmt.niceDate(DateTime.now())}',
            child: vm.todayLoading && today == null
                ? const LinearProgressIndicator()
                : today == null
                ? Text(
                    vm.todayError?.message ?? '—',
                    style: const TextStyle(color: AppColors.muted),
                  )
                : Row(
                    children: [
                      _Stat(value: '${today.stopCount}', label: 'Stops'),
                      _Stat(
                        value: '${today.completedCount}',
                        label: 'Completed',
                      ),
                      _Stat(value: '${today.routes.length}', label: 'Routes'),
                    ],
                  ),
          ),
          _InfoCard(
            title: 'ROUTES',
            child: Text(routeNames.isEmpty ? '—' : routeNames.join(', ')),
          ),
          _InfoCard(
            title: 'VEHICLE',
            child: Text(vehicleText.isEmpty ? '—' : '🚐 $vehicleText'),
          ),
          const SizedBox(height: 6),
          Card(
            child: ListTile(
              leading: const Icon(Icons.logout, color: AppColors.danger),
              title: const Text(
                'Sign out',
                style: TextStyle(
                  color: AppColors.danger,
                  fontWeight: FontWeight.w700,
                ),
              ),
              onTap: () => _signOut(context),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            '${AppConfig.appName} · ${AppConfig.apiBaseUrl}',
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.muted, fontSize: 11),
          ),
        ],
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 11,
                  letterSpacing: 0.5,
                  color: AppColors.muted,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 6),
              DefaultTextStyle.merge(
                style: const TextStyle(fontSize: 14),
                child: child,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) => Expanded(
    child: Column(
      children: [
        Text(
          value,
          style: const TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w800,
            color: AppColors.brandDark,
          ),
        ),
        Text(
          label,
          style: const TextStyle(fontSize: 12, color: AppColors.muted),
        ),
      ],
    ),
  );
}
