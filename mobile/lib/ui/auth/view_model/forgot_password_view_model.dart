import 'dart:async';

import '../../../data/repositories/auth_repository.dart';
import '../../../data/services/api_exception.dart';
import '../../core/view_model.dart';

/// Forgot password: step 1 emails a 6-digit code, step 2 sets the new
/// password with it.
class ForgotPasswordViewModel extends ViewModel {
  ForgotPasswordViewModel({required AuthRepository auth}) : _auth = auth;

  static const resendSeconds = 60;

  final AuthRepository _auth;
  Timer? _timer;

  String? _email;
  bool _busy = false;
  String? _error;
  int _cooldown = 0;

  /// The address the code was sent to; null while on the email step.
  String? get email => _email;
  bool get codeSent => _email != null;
  bool get busy => _busy;
  String? get error => _error;

  /// Seconds until "Resend code" is allowed again.
  int get cooldown => _cooldown;

  Future<void> sendCode(String email) async {
    if (_busy) return;
    _start();
    try {
      await _auth.requestPasswordReset(email);
      _email = email.trim();
      _startCooldown();
    } catch (e) {
      _error = ApiException.from(e).message;
    } finally {
      _finish();
    }
  }

  Future<void> resend() async {
    final email = _email;
    if (email == null || _cooldown > 0) return;
    await sendCode(email);
  }

  /// Returns true when the password was changed.
  Future<bool> reset(String code, String newPassword) async {
    final email = _email;
    if (_busy || email == null) return false;
    _start();
    try {
      await _auth.resetPassword(email, code, newPassword);
      return true;
    } catch (e) {
      _error = ApiException.from(e).message;
      return false;
    } finally {
      _finish();
    }
  }

  /// Back to the email step.
  void changeEmail() {
    _email = null;
    _error = null;
    notifyListeners();
  }

  void _start() {
    _busy = true;
    _error = null;
    notifyListeners();
  }

  void _finish() {
    _busy = false;
    notifyListeners();
  }

  void _startCooldown() {
    _timer?.cancel();
    _cooldown = resendSeconds;
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      _cooldown--;
      if (_cooldown <= 0) t.cancel();
      notifyListeners();
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}
