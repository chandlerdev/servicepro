import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:provider/provider.dart';

import '../../../config/app_theme.dart';
import '../../../data/models/stop_models.dart';
import '../../core/utils/formatters.dart';
import '../../core/utils/launcher.dart';
import '../../core/widgets/common.dart';
import '../view_model/map_view_model.dart';
import '../widgets/marker_icons.dart';
import '../widgets/nav_banner.dart';

const _depotLabel = 'HQ';

/// Map tab. State comes from [MapViewModel]; the camera controller and the
/// marker bitmap cache are view concerns and stay here.
class MapScreen extends StatefulWidget {
  const MapScreen({super.key});

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> {
  final _icons = MarkerIconCache();
  GoogleMapController? _map;
  int? _handledFocus;
  String? _fittedDate;

  // ---------------------------------------------------------------- markers

  (String, Color) _spec(Stop s) =>
      ('${s.order}', StopNumber.colorFor(s.status));

  void _ensureIcons(TechStops data, double dpr) {
    final specs = <(String, Color)>[
      (_depotLabel, AppColors.navy),
      for (final e in data.allStops) _spec(e.stop),
    ];
    _icons.ensure(specs, dpr).then((added) {
      if (added && mounted) setState(() {});
    });
  }

  Set<Marker> _markers(TechStops data, double dpr) {
    BitmapDescriptor icon(String label, Color color, double hue) =>
        _icons.get(label, color, dpr) ??
        BitmapDescriptor.defaultMarkerWithHue(hue);

    final markers = <Marker>{};
    final o = data.office;
    if (o.hasCoords) {
      markers.add(
        Marker(
          markerId: const MarkerId('depot'),
          position: LatLng(o.lat!, o.lng!),
          icon: icon(_depotLabel, AppColors.navy, BitmapDescriptor.hueAzure),
          anchor: const Offset(0.5, 0.5),
          zIndexInt: 0,
          infoWindow: InfoWindow(title: '🏢 ${o.name}', snippet: o.address),
        ),
      );
    }
    for (final e in data.allStops) {
      final s = e.stop;
      if (!s.hasCoords) continue;
      final (label, color) = _spec(s);
      markers.add(
        Marker(
          markerId: MarkerId('stop_${s.appointmentId}'),
          position: LatLng(s.lat!, s.lng!),
          icon: icon(label, color, s.isDelayed ? BitmapDescriptor.hueRed : 160),
          anchor: const Offset(0.5, 0.5),
          zIndexInt: 10 + s.order,
          infoWindow: InfoWindow(
            title: '${s.order}. ${s.customerName}',
            snippet: '${s.taskName} · ETA ${Fmt.hm(s.plannedArrival)}',
          ),
          onTap: () => context.read<MapViewModel>().focus(s.appointmentId),
        ),
      );
    }
    return markers;
  }

  Set<Polyline> _polylines(TechStops data) {
    final o = data.office;
    final lines = <Polyline>{};
    for (final r in data.routes) {
      final pts = <LatLng>[
        if (o.hasCoords) LatLng(o.lat!, o.lng!),
        for (final s in r.stops)
          if (s.hasCoords && !s.isCancelled) LatLng(s.lat!, s.lng!),
      ];
      if (pts.length < 2) continue;
      lines.add(
        Polyline(
          polylineId: PolylineId('route_${r.routeId}'),
          points: pts,
          color: AppColors.brand.withValues(alpha: 0.85),
          width: 4,
          geodesic: true,
        ),
      );
    }
    return lines;
  }

  // ----------------------------------------------------------------- camera

  List<LatLng> _allPoints(TechStops data) => [
    if (data.office.hasCoords) LatLng(data.office.lat!, data.office.lng!),
    for (final e in data.allStops)
      if (e.stop.hasCoords) LatLng(e.stop.lat!, e.stop.lng!),
  ];

  Future<void> _fitAll(TechStops data) async {
    final pts = _allPoints(data);
    final c = _map;
    if (pts.isEmpty || c == null) return;
    _fittedDate = data.date;
    try {
      if (pts.length == 1) {
        await c.animateCamera(CameraUpdate.newLatLngZoom(pts.first, 13));
        return;
      }
      var minLat = pts.first.latitude, maxLat = minLat;
      var minLng = pts.first.longitude, maxLng = minLng;
      for (final p in pts) {
        minLat = math.min(minLat, p.latitude);
        maxLat = math.max(maxLat, p.latitude);
        minLng = math.min(minLng, p.longitude);
        maxLng = math.max(maxLng, p.longitude);
      }
      await c.animateCamera(
        CameraUpdate.newLatLngBounds(
          LatLngBounds(
            southwest: LatLng(minLat, minLng),
            northeast: LatLng(maxLat, maxLng),
          ),
          56,
        ),
      );
    } catch (_) {
      // Map not laid out yet; ignore.
    }
  }

  Future<void> _focusStop(Stop s) async {
    final c = _map;
    if (!s.hasCoords || c == null) return;
    try {
      await c.animateCamera(
        CameraUpdate.newLatLngZoom(LatLng(s.lat!, s.lng!), 15),
      );
      await c.showMarkerInfoWindow(MarkerId('stop_${s.appointmentId}'));
    } catch (_) {
      // Controller disposed / map not ready.
    }
  }

  void _syncCamera(TechStops data, int? focus) {
    if (focus != _handledFocus) {
      _handledFocus = focus;
      final rs = focus == null ? null : data.findStop(focus);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        if (rs != null) {
          _focusStop(rs.stop);
        } else {
          _fitAll(data);
        }
      });
    } else if (focus == null && _fittedDate != data.date) {
      // A different day was loaded: re-fit the camera.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _fitAll(data);
      });
    }
  }

  // ------------------------------------------------------------------ build

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<MapViewModel>();
    final focus = vm.focusId;
    final loading = vm.isLoading;
    final data = vm.data;

    if (data == null && loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (vm.error != null && !loading) {
      return ErrorView(message: vm.error!.message, onRetry: vm.retry);
    }
    if (data == null) {
      return const Center(child: CircularProgressIndicator());
    }
    final leg = vm.navLeg;

    final banner = leg == null
        ? null
        : NavBanner(leg: leg, onClose: vm.clearFocus);

    final mapsConfigured = vm.mapsConfigured;
    if (mapsConfigured == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (!mapsConfigured) {
      return _NoMapFallback(data: data, banner: banner);
    }

    final dpr = MediaQuery.devicePixelRatioOf(context);
    _ensureIcons(data, dpr);
    _syncCamera(data, focus);

    final pts = _allPoints(data);
    final initial = CameraPosition(
      target: pts.isNotEmpty ? pts.first : const LatLng(29.752, -95.37),
      zoom: 11,
    );

    return Stack(
      children: [
        GoogleMap(
          initialCameraPosition: initial,
          markers: _markers(data, dpr),
          polylines: _polylines(data),
          myLocationEnabled: false,
          myLocationButtonEnabled: false,
          mapToolbarEnabled: false,
          zoomControlsEnabled: false,
          padding: EdgeInsets.only(top: banner != null ? 150 : 50),
          onMapCreated: (c) {
            _map = c;
            final rs = focus == null ? null : data.findStop(focus);
            if (rs != null) {
              _focusStop(rs.stop);
            } else {
              Future.delayed(const Duration(milliseconds: 300), () {
                if (mounted) _fitAll(data);
              });
            }
          },
        ),
        Positioned(
          left: 12,
          right: 12,
          top: 10,
          child: banner ?? _RouteSummaryChip(data: data),
        ),
        Positioned(
          right: 12,
          bottom: 16,
          child: FloatingActionButton.small(
            heroTag: 'fit',
            tooltip: 'Show whole route',
            backgroundColor: Colors.white,
            foregroundColor: AppColors.brandDark,
            onPressed: () {
              vm.clearFocus();
              _fitAll(data);
            },
            child: const Icon(Icons.zoom_out_map),
          ),
        ),
        if (loading)
          const Positioned(
            left: 0,
            right: 0,
            top: 0,
            child: LinearProgressIndicator(minHeight: 3),
          ),
        if (data.allStops.isEmpty && !loading)
          const Center(
            child: Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Text('No stops for this date'),
              ),
            ),
          ),
      ],
    );
  }
}

class _RouteSummaryChip extends StatelessWidget {
  const _RouteSummaryChip({required this.data});

  final TechStops data;

  @override
  Widget build(BuildContext context) {
    final date = Fmt.parseApiDate(data.date);
    final parts = [
      if (date != null) Fmt.niceDate(date),
      for (final r in data.routes)
        r.totalKm != null
            ? '${r.routeName}: ${Fmt.km(r.totalKm!)}'
            : r.routeName,
    ];
    if (parts.isEmpty) return const SizedBox.shrink();
    return Align(
      alignment: Alignment.topLeft,
      child: Material(
        color: Colors.white,
        elevation: 3,
        borderRadius: BorderRadius.circular(20),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          child: Text(
            '🗺️ ${parts.join(' · ')}',
            style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
          ),
        ),
      ),
    );
  }
}

/// Shown when no Google Maps API key is configured: ordered stop list with
/// Google Maps hand-off links instead of the embedded map.
class _NoMapFallback extends StatelessWidget {
  const _NoMapFallback({required this.data, required this.banner});

  final TechStops data;
  final Widget? banner;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        if (banner != null) ...[banner!, const SizedBox(height: 10)],
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppColors.warnBg,
            border: Border.all(color: AppColors.warnBorder),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Text(
            'Map preview unavailable — no Google Maps API key is configured in '
            'this build. Showing your stops in route order; tap ↗ for '
            'turn-by-turn directions in Google Maps.',
            style: TextStyle(color: Color(0xFF7C5A10), fontSize: 13),
          ),
        ),
        const SizedBox(height: 10),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const CircleAvatar(
                  radius: 14,
                  backgroundColor: AppColors.navy,
                  child: Text(
                    _depotLabel,
                    style: TextStyle(fontSize: 10, color: Colors.white),
                  ),
                ),
                title: Text(data.office.name),
                subtitle: Text(data.office.address),
              ),
              for (final e in data.allStops)
                ListTile(
                  leading: StopNumber(e.stop.order, status: e.stop.status),
                  title: Text(e.stop.customerName),
                  subtitle: Text(
                    '${e.stop.address}\nETA ${Fmt.hm(e.stop.plannedArrival)}',
                  ),
                  isThreeLine: true,
                  onTap: () => context.read<MapViewModel>().focus(
                    e.stop.appointmentId,
                  ),
                  trailing: e.stop.hasCoords
                      ? IconButton(
                          tooltip: 'Open in Google Maps',
                          icon: const Icon(Icons.open_in_new),
                          onPressed: () =>
                              openDirections(context, e.stop.lat!, e.stop.lng!),
                        )
                      : null,
                ),
            ],
          ),
        ),
      ],
    );
  }
}
