import 'package:flutter/foundation.dart';

/// Base class for ViewModels: a [ChangeNotifier] that silently ignores
/// notifications after [dispose] (async commands may complete after the
/// screen that owned the ViewModel is gone, e.g. on sign-out).
abstract class ViewModel extends ChangeNotifier {
  bool _disposed = false;

  bool get isDisposed => _disposed;

  @override
  void notifyListeners() {
    if (!_disposed) super.notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
