import '../../../data/repositories/auth_repository.dart';
import '../../../data/services/api_exception.dart';
import '../../core/view_model.dart';

/// Sign-in form state. On success the router redirects to the home shell.
class LoginViewModel extends ViewModel {
  LoginViewModel({required AuthRepository auth}) : _auth = auth {
    _auth.addListener(notifyListeners);
  }

  final AuthRepository _auth;

  bool _busy = false;
  String? _error;

  bool get busy => _busy;

  /// Login error, else the session message (e.g. "session expired").
  String? get message => _error ?? _auth.message;

  /// Returns true when signed in.
  Future<bool> login(String email, String password) async {
    if (_busy) return false;
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      await _auth.login(email, password);
      return true;
    } catch (e) {
      _error = ApiException.from(e).message;
      return false;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _auth.removeListener(notifyListeners);
    super.dispose();
  }
}
