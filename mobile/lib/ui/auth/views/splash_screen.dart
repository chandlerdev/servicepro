import 'package:flutter/material.dart';

/// Shown while the stored session is being restored. Matches the native splash
/// (flutter_native_splash: same colour, logo and wordmark) so there is no visible jump.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor:
        Colors.white, // same as the native splash (flutter_native_splash)
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
