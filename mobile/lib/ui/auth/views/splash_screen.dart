import 'package:flutter/material.dart';

import '../../../config/app_theme.dart';

/// Shown while the stored session is being restored. Matches the native splash
/// (flutter_native_splash: same colour, logo and wordmark) so there is no visible jump.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: AppColors.brandDark,
    body: SafeArea(
      child: Column(
        children: [
          Expanded(
            child: Center(
              child: Image.asset(
                'assets/splash/logo.png',
                width: 240,
                height: 240,
              ),
            ),
          ),
          Image.asset('assets/splash/branding.png', width: 180),
          const SizedBox(height: 24),
        ],
      ),
    ),
  );
}
