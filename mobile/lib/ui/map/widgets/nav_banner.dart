import 'package:flutter/material.dart';

import '../../../config/app_theme.dart';
import '../../core/utils/formatters.dart';
import '../../core/utils/launcher.dart';
import '../view_model/nav_leg.dart';

/// Dark "Navigating to …" card shown on top of the map.
class NavBanner extends StatelessWidget {
  const NavBanner({super.key, required this.leg, required this.onClose});

  final NavLeg leg;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final s = leg.stop;
    final info = [
      if (leg.km != null) Fmt.km(leg.km!),
      if (leg.minutes != null) '~${leg.minutes} min drive',
      'ETA ${Fmt.hm(s.plannedArrival)}',
    ].join(' · ');
    return Material(
      color: AppColors.navy,
      elevation: 6,
      borderRadius: BorderRadius.circular(13),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(13, 10, 4, 10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(top: 2),
              child: Text('🧭', style: TextStyle(fontSize: 18)),
            ),
            const SizedBox(width: 9),
            Expanded(
              child: DefaultTextStyle(
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 13,
                  height: 1.4,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Navigating to ${s.customerName}',
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                      ),
                    ),
                    Text(
                      s.address,
                      style: const TextStyle(color: Color(0xFFCBD7E3)),
                    ),
                    Text(info),
                    if (s.hasCoords)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: FilledButton(
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.brand,
                            minimumSize: const Size(0, 36),
                            padding: const EdgeInsets.symmetric(horizontal: 14),
                          ),
                          onPressed: () =>
                              openDirections(context, s.lat!, s.lng!),
                          child: const Text('Open in Google Maps ↗'),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            IconButton(
              tooltip: 'Close',
              onPressed: onClose,
              icon: const Icon(Icons.close, color: Color(0xFF9FB3C8)),
            ),
          ],
        ),
      ),
    );
  }
}
