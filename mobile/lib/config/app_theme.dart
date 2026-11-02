import 'package:flutter/material.dart';

/// Palette from the ServProWare web UI.
class AppColors {
  AppColors._();

  // ServProWare brand: blue #0060F0 (main) + green #22B14C (accent / success)
  static const brand = Color(0xFF0060F0);
  static const brandDark = Color(0xFF004BBD);
  static const brandLight = Color(0xFFE5EEFE);
  static const accent = Color(0xFF22B14C);
  static const ink = Color(0xFF1A2430);
  static const muted = Color(0xFF66748A);
  static const line = Color(0xFFE3E8EF);
  static const bg = Color(0xFFF2F5F8);
  static const danger = Color(0xFFC93A3A);
  static const warn = Color(0xFFB97A12);
  static const ok = Color(0xFF188A3B); // brand green, deepened for readable text
  static const navy = Color(0xFF122333);
  static const doneStop = Color(0xFF9FB3A8);

  static const warnBg = Color(0xFFFFF4E0);
  static const warnBorder = Color(0xFFEED9AC);
  static const okBg = Color(0xFFE7F6EC);
  static const okBorder = Color(0xFFB9E3C6);
}

class AppFonts {
  AppFonts._();

  static const family = 'Poppins';
}

/// Background / foreground for an AppointmentStatus chip.
(Color bg, Color fg) statusColors(String status) => switch (status) {
  'SCHEDULED' => (const Color(0xFFE8EEFB), const Color(0xFF2C56B0)),
  'CONFIRMED' => (const Color(0xFFE6F4F1), const Color(0xFF0A6C5C)),
  'EN_ROUTE' => (const Color(0xFFEEE5FB), const Color(0xFF6B3FB2)),
  'DELAYED' => (const Color(0xFFFDECEC), const Color(0xFFC93A3A)),
  'COMPLETED' => (AppColors.okBg, AppColors.ok),
  'CANCELLED' => (const Color(0xFFF0F0F0), const Color(0xFF777777)),
  _ => (const Color(0xFFFFF4E0), const Color(0xFFB97A12)),
};

ThemeData buildAppTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: AppColors.brand,
    primary: AppColors.brand,
    onPrimary: Colors.white,
    secondary: AppColors.brandDark,
    error: AppColors.danger,
    surface: Colors.white,
  );
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.bg,
    // Bundled font (see pubspec.yaml); applies to every Text in the app.
    fontFamily: AppFonts.family,
  );
  final btnShape = RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(10),
  );
  return base.copyWith(
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.brandDark,
      foregroundColor: Colors.white,
      elevation: 0,
      centerTitle: false,
    ),
    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 1.5,
      shadowColor: const Color(0x22142332),
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Colors.white,
      indicatorColor: AppColors.brandLight,
      labelTextStyle: WidgetStateProperty.resolveWith(
        (s) => TextStyle(
          fontSize: 12,
          fontWeight: s.contains(WidgetState.selected)
              ? FontWeight.w700
              : FontWeight.w500,
          color: s.contains(WidgetState.selected)
              ? AppColors.brandDark
              : AppColors.muted,
        ),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.brand,
        shape: btnShape,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(shape: btnShape),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: AppColors.line),
      ),
    ),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
  );
}
