import '../../../config/app_config.dart';
import '../../../data/models/stop_models.dart';
import '../../../data/services/api_exception.dart';
import '../../core/view_model.dart';
import '../../home/view_model/home_view_model.dart';
import '../../stops/view_model/stops_view_model.dart';
import 'nav_leg.dart';

/// State of the Map tab: the selected day's stops (from [StopsViewModel]),
/// the focused stop / navigation leg (from [HomeViewModel]) and whether a
/// Google Maps key is configured natively.
class MapViewModel extends ViewModel {
  MapViewModel({
    required StopsViewModel stops,
    required HomeViewModel home,
    Future<bool> Function() isMapsConfigured = AppConfig.isMapsConfigured,
  }) : _stops = stops,
       _home = home {
    _stops.addListener(notifyListeners);
    _home.addListener(notifyListeners);
    isMapsConfigured().then((v) {
      _mapsConfigured = v;
      notifyListeners();
    });
  }

  final StopsViewModel _stops;
  final HomeViewModel _home;
  bool? _mapsConfigured;

  /// null while the native check is pending.
  bool? get mapsConfigured => _mapsConfigured;

  TechStops? get data => _stops.data;
  bool get isLoading => _stops.isLoading;
  ApiException? get error => _stops.error;

  /// Appointment being navigated to (null = whole-route overview).
  int? get focusId => _home.mapFocus;

  /// Leg to the focused stop, or null when nothing (known) is focused.
  NavLeg? get navLeg {
    final f = focusId, d = data;
    return f == null || d == null ? null : NavLeg.forStop(d, f);
  }

  void focus(int appointmentId) => _home.focusStop(appointmentId);
  void clearFocus() => _home.clearMapFocus();
  Future<void> retry() => _stops.load();

  @override
  void dispose() {
    _stops.removeListener(notifyListeners);
    _home.removeListener(notifyListeners);
    super.dispose();
  }
}
