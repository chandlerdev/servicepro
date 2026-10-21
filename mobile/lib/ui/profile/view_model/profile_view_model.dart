import 'dart:async';

import '../../../data/models/stop_models.dart';
import '../../../data/models/tech_profile.dart';
import '../../../data/models/user.dart';
import '../../../data/repositories/auth_repository.dart';
import '../../../data/repositories/profile_repository.dart';
import '../../../data/repositories/stops_repository.dart';
import '../../../data/services/api_exception.dart';
import '../../core/view_model.dart';

/// Profile tab: technician profile, today's stats, sign out.
class ProfileViewModel extends ViewModel {
  ProfileViewModel({
    required ProfileRepository profileRepository,
    required StopsRepository stopsRepository,
    required AuthRepository authRepository,
  }) : _profileRepo = profileRepository,
       _stopsRepo = stopsRepository,
       _auth = authRepository {
    _auth.addListener(notifyListeners);
    // A stop action changed today's numbers: reload them.
    _changesSub = _stopsRepo.changes.listen((_) => _loadToday());
  }

  final ProfileRepository _profileRepo;
  final StopsRepository _stopsRepo;
  final AuthRepository _auth;
  late final StreamSubscription<void> _changesSub;

  TechProfile? _profile;
  ApiException? _profileError;
  TechStops? _today;
  bool _todayLoading = false;
  ApiException? _todayError;

  TechProfile? get profile => _profile;
  ApiException? get profileError => _profileError;

  /// Today's stops regardless of the day selected on My Stops.
  TechStops? get today => _today;
  bool get todayLoading => _todayLoading;
  ApiException? get todayError => _todayError;

  /// User from the auth session (fallback while the profile loads).
  User? get authUser => _auth.user;

  /// Loads profile and today's stops in parallel (also pull-to-refresh).
  /// Errors are exposed via [profileError] / [todayError]; data is kept.
  Future<void> load() => Future.wait([_loadProfile(), _loadToday()]);

  Future<void> _loadProfile() async {
    try {
      _profile = await _profileRepo.me();
      _profileError = null;
    } catch (e) {
      _profileError = ApiException.from(e);
    }
    notifyListeners();
  }

  Future<void> _loadToday() async {
    _todayLoading = true;
    notifyListeners();
    try {
      _today = await _stopsRepo.fetchStops(DateTime.now());
      _todayError = null;
    } catch (e) {
      _todayError = ApiException.from(e);
    }
    _todayLoading = false;
    notifyListeners();
  }

  Future<void> signOut() => _auth.logout();

  @override
  void dispose() {
    _changesSub.cancel();
    _auth.removeListener(notifyListeners);
    super.dispose();
  }
}
