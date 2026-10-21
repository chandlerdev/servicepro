import '../../../data/repositories/auth_repository.dart';
import '../../core/utils/formatters.dart';
import '../../core/view_model.dart';

enum HomeTab { stops, map, alerts, profile }

/// App-level UI state shared by the home tabs: selected tab, the day shown on
/// My Stops / Map, and the stop the Map tab is "navigating" to.
///
/// Resets itself whenever a new session starts so a technician never sees
/// the previous user's selection.
class HomeViewModel extends ViewModel {
  HomeViewModel({required AuthRepository auth}) : _auth = auth {
    _wasAuthenticated = auth.isAuthenticated;
    _auth.addListener(_onAuthChanged);
  }

  final AuthRepository _auth;
  late bool _wasAuthenticated;

  HomeTab _tab = HomeTab.stops;
  DateTime _selectedDate = Fmt.dateOnly(DateTime.now());
  int? _mapFocus;

  HomeTab get tab => _tab;

  /// Day shown on My Stops / Map (local date, time stripped).
  DateTime get selectedDate => _selectedDate;

  /// Appointment the Map tab is navigating to (null = route overview).
  int? get mapFocus => _mapFocus;

  /// Signed-in user's name (header fallback before stops load).
  String? get userName => _auth.user?.name;

  void _onAuthChanged() {
    final now = _auth.isAuthenticated;
    if (now && !_wasAuthenticated) {
      _tab = HomeTab.stops;
      _selectedDate = Fmt.dateOnly(DateTime.now());
      _mapFocus = null;
    }
    _wasAuthenticated = now;
    notifyListeners();
  }

  void selectTab(HomeTab tab) {
    if (tab != HomeTab.map) _mapFocus = null;
    _tab = tab;
    notifyListeners();
  }

  /// "🧭 Navigate" on a stop card: open the Map tab focused on that stop.
  void navigateToStop(int appointmentId) {
    _mapFocus = appointmentId;
    _tab = HomeTab.map;
    notifyListeners();
  }

  void focusStop(int appointmentId) {
    _mapFocus = appointmentId;
    notifyListeners();
  }

  void clearMapFocus() {
    if (_mapFocus == null) return;
    _mapFocus = null;
    notifyListeners();
  }

  void setDate(DateTime d) => _setDate(Fmt.dateOnly(d));
  void previousDay() => _setDate(
    DateTime(_selectedDate.year, _selectedDate.month, _selectedDate.day - 1),
  );
  void nextDay() => _setDate(
    DateTime(_selectedDate.year, _selectedDate.month, _selectedDate.day + 1),
  );
  void today() => _setDate(Fmt.dateOnly(DateTime.now()));

  void _setDate(DateTime d) {
    if (d == _selectedDate) return;
    _selectedDate = d;
    notifyListeners();
  }

  @override
  void dispose() {
    _auth.removeListener(_onAuthChanged);
    super.dispose();
  }
}
