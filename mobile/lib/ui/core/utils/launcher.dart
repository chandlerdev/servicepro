import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'geo.dart';
import 'ui.dart';

/// Opens Google Maps (app if installed, else browser) with driving directions.
Future<void> openDirections(
  BuildContext context,
  double lat,
  double lng,
) async {
  final ok = await launchUrl(
    googleMapsDirections(lat, lng),
    mode: LaunchMode.externalApplication,
  ).catchError((_) => false);
  if (!ok && context.mounted) showSnack(context, 'Could not open Google Maps');
}
