import 'dart:async';

import '../../../config/app_config.dart';
import '../../../data/models/stop_models.dart';
import '../../../data/repositories/stops_repository.dart';
import '../../../data/services/api_exception.dart';
import '../../../utils/result.dart';
import '../../core/view_model.dart';
import '../../home/view_model/home_view_model.dart';

/// Action currently running on a stop card.
enum StopAction { onMyWay, delay, complete }

/// Stops for the day selected in [HomeViewModel] plus the stop actions.
///
/// Shared by My Stops, the Map tab and the home header. Reloads when the
/// selected day changes and auto-refreshes while the app is foregrounded.
class StopsViewModel extends ViewModel {
  StopsViewModel({
    required StopsRepository repository,
    required HomeViewModel home,
    this.autoRefreshInterval = AppConfig.autoRefreshInterval,
  }) : _repo = repository,
       _home = home,
       _date = home.selectedDate {
    _home.addListener(_onHomeChanged);
  }

  final StopsRepository _repo;
  final HomeViewModel _home;
  final Duration autoRefreshInterval;

  DateTime _date;
  TechStops? _data;
  bool _loading = false;
  ApiException? _error;
  final Map<int, StopAction> _busy = {};
  int _seq = 0;
  Timer? _timer;

  /// Last successfully loaded stops (kept while reloading).
  TechStops? get data => _data;

  /// A full (re)load is running — not set by pull-to-refresh / auto-refresh.
  bool get isLoading => _loading;

  /// Error of the last full load (null once a load/refresh succeeds).
  ApiException? get error => _error;

  /// Action running on [appointmentId], or null if the card is idle.
  StopAction? busyAction(int appointmentId) => _busy[appointmentId];
  bool isBusy(int appointmentId) => _busy.containsKey(appointmentId);

  void _onHomeChanged() {
    if (_home.selectedDate != _date) load();
  }

  // ---------------------------------------------------------------- loading

  /// Loads the selected day, showing a spinner (initial load, day change,
  /// Retry button).
  Future<void> load() async {
    final seq = ++_seq;
    final date = _date = _home.selectedDate;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _repo.fetchStops(date);
      if (seq != _seq) return; // superseded by a newer load
      _data = result;
    } catch (e) {
      if (seq != _seq) return;
      _error = ApiException.from(e);
    }
    _loading = false;
    notifyListeners();
  }

  /// Re-fetches without dropping the current list (pull-to-refresh).
  /// Returns [Err] on failure so the View can report it.
  Future<Result<void>> refresh() async {
    final date = _home.selectedDate;
    final seq = _seq;
    try {
      final result = await _repo.fetchStops(date);
      // Ignore if the user switched day / a full load started meanwhile.
      if (isDisposed || seq != _seq || _home.selectedDate != date) {
        return const Ok(null);
      }
      _data = result;
      _error = null;
      notifyListeners();
      return const Ok(null);
    } catch (e) {
      return Err(ApiException.from(e));
    }
  }

  /// Silent background refresh (errors ignored).
  Future<void> autoRefresh() async {
    if (isDisposed || _loading) return;
    if (_error != null && _data == null) {
      await load();
      return;
    }
    await refresh();
  }

  // ----------------------------------------------------------- auto-refresh

  void startAutoRefresh() {
    _timer?.cancel();
    _timer = Timer.periodic(autoRefreshInterval, (_) => autoRefresh());
  }

  void stopAutoRefresh() {
    _timer?.cancel();
    _timer = null;
  }

  /// App came back to the foreground: refresh now and restart the timer.
  void onResume() {
    autoRefresh();
    startAutoRefresh();
  }

  // ---------------------------------------------------------------- actions

  /// "On my way": texts the customer. Returns the updated stop.
  Future<Result<Stop>> onMyWay(int appointmentId) => _run(
    appointmentId,
    StopAction.onMyWay,
    () => _repo.onMyWay(appointmentId),
  );

  /// Reports a delay; returns how many customers were notified.
  Future<Result<int>> delay(int appointmentId, int minutes, String? reason) =>
      _run(
        appointmentId,
        StopAction.delay,
        () => _repo.reportDelay(appointmentId, minutes, reason),
      );

  /// Completes the job (the customer is texted review links).
  Future<Result<CompleteResult>> complete(int appointmentId, String? notes) =>
      _run(
        appointmentId,
        StopAction.complete,
        () => _repo.complete(appointmentId, notes),
      );

  Future<Result<T>> _run<T>(
    int appointmentId,
    StopAction action,
    Future<T> Function() fn,
  ) async {
    if (_busy.containsKey(appointmentId)) {
      return Err(ApiException('Please wait for the current action to finish.'));
    }
    _busy[appointmentId] = action;
    notifyListeners();
    try {
      final value = await fn();
      // Action succeeded; a failed refresh is retried by auto-refresh.
      await refresh();
      return Ok(value);
    } catch (e) {
      return Err(ApiException.from(e));
    } finally {
      _busy.remove(appointmentId);
      notifyListeners();
    }
  }

  @override
  void dispose() {
    stopAutoRefresh();
    _home.removeListener(_onHomeChanged);
    super.dispose();
  }
}
