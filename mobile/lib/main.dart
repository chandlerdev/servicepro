import 'package:flutter/material.dart';
import 'package:flutter_native_splash/flutter_native_splash.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import 'app.dart';
import 'data/repositories/alerts_repository.dart';
import 'data/repositories/auth_repository.dart';
import 'data/repositories/profile_repository.dart';
import 'data/repositories/stops_repository.dart';
import 'data/services/api_client.dart';
import 'data/services/token_storage.dart';
import 'ui/home/view_model/home_view_model.dart';

Future<void> main() async {
  final binding = WidgetsFlutterBinding.ensureInitialized();
  // Keep the native splash (flutter_native_splash) up until the saved session is checked.
  FlutterNativeSplash.preserve(widgetsBinding: binding);
  await initializeDateFormatting();
  runApp(
    MultiProvider(
      providers: [
        // Services
        Provider<TokenStorage>(create: (_) => TokenStorage()),
        Provider<ApiClient>(
          create: (c) => ApiClient(tokens: c.read<TokenStorage>()),
          dispose: (_, api) => api.close(),
        ),
        // Repositories
        ChangeNotifierProvider<AuthRepository>(
          create: (c) {
            final auth = AuthRepository(
              api: c.read<ApiClient>(),
              tokens: c.read<TokenStorage>(),
            );
            auth.restore().whenComplete(_removeSplash);
            return auth;
          },
        ),
        Provider<StopsRepository>(
          create: (c) => StopsRepository(c.read<ApiClient>()),
          dispose: (_, r) => r.dispose(),
        ),
        Provider<AlertsRepository>(
          create: (c) => AlertsRepository(c.read<ApiClient>()),
        ),
        Provider<ProfileRepository>(
          create: (c) => ProfileRepository(c.read<ApiClient>()),
        ),
        // App-level view models
        ChangeNotifierProvider<HomeViewModel>(
          create: (c) => HomeViewModel(auth: c.read<AuthRepository>()),
        ),
      ],
      child: const ServiceProsTechApp(),
    ),
  );
  // Never keep the splash longer than this, even if the server is slow.
  Future<void>.delayed(const Duration(seconds: 4), _removeSplash);
}

bool _splashRemoved = false;

void _removeSplash() {
  if (_splashRemoved) return;
  _splashRemoved = true;
  FlutterNativeSplash.remove();
}
