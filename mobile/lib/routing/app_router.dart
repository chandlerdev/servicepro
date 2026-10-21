import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../data/repositories/alerts_repository.dart';
import '../data/repositories/auth_repository.dart';
import '../data/repositories/profile_repository.dart';
import '../data/repositories/stops_repository.dart';
import '../ui/alerts/view_model/alerts_view_model.dart';
import '../ui/auth/view_model/forgot_password_view_model.dart';
import '../ui/auth/view_model/login_view_model.dart';
import '../ui/auth/views/forgot_password_screen.dart';
import '../ui/auth/views/login_screen.dart';
import '../ui/auth/views/splash_screen.dart';
import '../ui/home/view_model/home_view_model.dart';
import '../ui/home/views/home_shell.dart';
import '../ui/map/view_model/map_view_model.dart';
import '../ui/profile/view_model/profile_view_model.dart';
import '../ui/stops/view_model/stops_view_model.dart';

class AppRoutes {
  AppRoutes._();
  static const splash = '/splash';
  static const login = '/login';
  static const forgotPassword = '/login/forgot-password';
  static const home = '/';
}

/// GoRouter with an auth redirect driven by [AuthRepository].
///
/// Screen view models are created in the route builders, so they live as long
/// as the route: signing out disposes the home shell's view models, and the
/// next sign-in starts from a clean slate (no previous user's data).
GoRouter createRouter(AuthRepository auth) => GoRouter(
  initialLocation: AppRoutes.splash,
  refreshListenable: auth,
  redirect: (context, state) {
    final loc = state.matchedLocation;
    switch (auth.status) {
      case AuthStatus.unknown:
        return loc == AppRoutes.splash ? null : AppRoutes.splash;
      case AuthStatus.unauthenticated:
        return (loc == AppRoutes.login || loc == AppRoutes.forgotPassword)
            ? null
            : AppRoutes.login;
      case AuthStatus.authenticated:
        return (loc == AppRoutes.login || loc == AppRoutes.splash)
            ? AppRoutes.home
            : null;
    }
  },
  routes: [
    GoRoute(path: AppRoutes.splash, builder: (_, _) => const SplashScreen()),
    GoRoute(
      path: AppRoutes.login,
      builder: (_, _) => ChangeNotifierProvider(
        create: (c) => LoginViewModel(auth: c.read<AuthRepository>()),
        child: const LoginScreen(),
      ),
      routes: [
        GoRoute(
          path: 'forgot-password',
          builder: (_, _) => ChangeNotifierProvider(
            create: (c) =>
                ForgotPasswordViewModel(auth: c.read<AuthRepository>()),
            child: const ForgotPasswordScreen(),
          ),
        ),
      ],
    ),
    GoRoute(
      path: AppRoutes.home,
      builder: (_, _) => MultiProvider(
        providers: [
          ChangeNotifierProvider(
            create: (c) => StopsViewModel(
              repository: c.read<StopsRepository>(),
              home: c.read<HomeViewModel>(),
            )..load(),
          ),
          ChangeNotifierProvider(
            create: (c) => MapViewModel(
              stops: c.read<StopsViewModel>(),
              home: c.read<HomeViewModel>(),
            ),
          ),
          ChangeNotifierProvider(
            create: (c) =>
                AlertsViewModel(repository: c.read<AlertsRepository>())..load(),
          ),
          ChangeNotifierProvider(
            create: (c) => ProfileViewModel(
              profileRepository: c.read<ProfileRepository>(),
              stopsRepository: c.read<StopsRepository>(),
              authRepository: c.read<AuthRepository>(),
            )..load(),
          ),
        ],
        child: const HomeShell(),
      ),
    ),
  ],
);
