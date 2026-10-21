import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'config/app_config.dart';
import 'config/app_theme.dart';
import 'data/repositories/auth_repository.dart';
import 'routing/app_router.dart';

class ServiceProsTechApp extends StatefulWidget {
  const ServiceProsTechApp({super.key});

  @override
  State<ServiceProsTechApp> createState() => _ServiceProsTechAppState();
}

class _ServiceProsTechAppState extends State<ServiceProsTechApp> {
  late final GoRouter _router = createRouter(context.read<AuthRepository>());

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp.router(
    title: AppConfig.appName,
    debugShowCheckedModeBanner: false,
    theme: buildAppTheme(),
    routerConfig: _router,
  );
}
