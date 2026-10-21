import '../data/services/api_exception.dart';

/// Outcome of a ViewModel command: either [Ok] with a value or [Err] with a
/// user-presentable [ApiException]. Views `switch` on it to show feedback.
sealed class Result<T> {
  const Result();

  const factory Result.ok(T value) = Ok<T>;
  const factory Result.error(ApiException error) = Err<T>;

  /// Runs [fn], converting any thrown error into [Err].
  static Future<Result<T>> guard<T>(Future<T> Function() fn) async {
    try {
      return Ok(await fn());
    } catch (e) {
      return Err(ApiException.from(e));
    }
  }
}

final class Ok<T> extends Result<T> {
  const Ok(this.value);
  final T value;
}

final class Err<T> extends Result<T> {
  const Err(this.error);
  final ApiException error;
}
