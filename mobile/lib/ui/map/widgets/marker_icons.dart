import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';

/// Draws circular numbered marker bitmaps (white ring, coloured fill, white
/// label) on a canvas and caches them per label/colour/pixel ratio.
class MarkerIconCache {
  final Map<String, BitmapDescriptor> _cache = {};
  final Set<String> _pending = {};

  static String _key(String label, Color color, double dpr) =>
      '$label|${color.toARGB32()}|$dpr';

  /// Cached icon, or null if it has not been drawn yet.
  BitmapDescriptor? get(String label, Color color, double dpr) =>
      _cache[_key(label, color, dpr)];

  /// Draws all missing icons; returns true if anything new was produced.
  Future<bool> ensure(
    Iterable<(String label, Color color)> specs,
    double dpr,
  ) async {
    var added = false;
    for (final (label, color) in specs) {
      final k = _key(label, color, dpr);
      if (_cache.containsKey(k) || _pending.contains(k)) continue;
      _pending.add(k);
      try {
        _cache[k] = await drawCircleMarker(label, color, dpr);
        added = true;
      } finally {
        _pending.remove(k);
      }
    }
    return added;
  }
}

/// Renders a [logicalSize]-dp circle marker as PNG bytes.
Future<BitmapDescriptor> drawCircleMarker(
  String label,
  Color color,
  double dpr, {
  double logicalSize = 32,
}) async {
  final px = logicalSize * dpr;
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);
  final center = Offset(px / 2, px / 2);
  final radius = px / 2;

  canvas.drawCircle(
    center.translate(0, dpr),
    radius - dpr * 1.5,
    Paint()
      ..color = Colors.black.withValues(alpha: 0.35)
      ..maskFilter = MaskFilter.blur(BlurStyle.normal, dpr * 1.5),
  );
  canvas.drawCircle(center, radius - dpr * 2, Paint()..color = Colors.white);
  canvas.drawCircle(center, radius - dpr * 4.5, Paint()..color = color);

  final tp = TextPainter(
    text: TextSpan(
      text: label,
      style: TextStyle(
        color: Colors.white,
        fontWeight: FontWeight.w800,
        fontSize: (label.length > 2 ? 10 : 13.5) * dpr,
      ),
    ),
    textDirection: TextDirection.ltr,
  )..layout();
  tp.paint(canvas, center - Offset(tp.width / 2, tp.height / 2));

  final image = await recorder.endRecording().toImage(px.round(), px.round());
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  return BitmapDescriptor.bytes(
    bytes!.buffer.asUint8List(),
    imagePixelRatio: dpr,
  );
}
