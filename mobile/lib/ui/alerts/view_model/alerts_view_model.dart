import 'package:flutter/material.dart' show DateTimeRange;

import '../../../data/models/app_notification.dart';
import '../../../data/models/page_result.dart';
import '../../../data/repositories/alerts_repository.dart';
import '../../../data/services/api_exception.dart';
import '../../../utils/result.dart';
import '../../core/view_model.dart';

/// Paged list of notifications with pull-to-refresh and infinite scroll.
class AlertsViewModel extends ViewModel {
  AlertsViewModel({required AlertsRepository repository}) : _repo = repository;

  static const pageSize = 20;

  final AlertsRepository _repo;

  List<AppNotification> _items = const [];
  int _page = 0;
  bool _hasMore = false;
  bool _loaded = false;
  bool _loading = false;
  ApiException? _error;
  bool _loadingMore = false;
  ApiException? _loadMoreError;
  int _seq = 0;
  DateTimeRange? _range;

  List<AppNotification> get items => _items;

  /// The chosen From/To dates; null = the server default (yesterday and today).
  DateTimeRange? get range => _range;

  /// Sets (or, with null, clears) the date filter and reloads the list.
  Future<void> setRange(DateTimeRange? range) {
    _range = range;
    return load();
  }

  static String _ymd(DateTime d) =>
      '${d.year.toString().padLeft(4, "0")}-'
      '${d.month.toString().padLeft(2, "0")}-'
      '${d.day.toString().padLeft(2, "0")}';

  Future<PageResult<AppNotification>> _fetch({int page = 0}) => _repo.fetch(
    page: page,
    size: pageSize,
    from: _range == null ? null : _ymd(_range!.start),
    to: _range == null ? null : _ymd(_range!.end),
  );

  bool get hasMore => _hasMore;

  /// True once the first page has been loaded successfully.
  bool get hasData => _loaded;

  /// First-page load in progress (initial / Retry).
  bool get isLoading => _loading;

  /// Error of the first-page load.
  ApiException? get error => _error;

  bool get loadingMore => _loadingMore;
  ApiException? get loadMoreError => _loadMoreError;

  /// Loads page 0 with a spinner (initial load, Retry button).
  Future<void> load() async {
    final seq = ++_seq;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final p = await _fetch();
      if (seq != _seq) return;
      _setFirstPage(p.content, !p.isLast);
    } catch (e) {
      if (seq != _seq) return;
      _error = ApiException.from(e);
    }
    _loading = false;
    notifyListeners();
  }

  /// Pull-to-refresh: reloads page 0, keeps the list on failure.
  Future<Result<void>> refresh() async {
    final seq = _seq;
    try {
      final p = await _fetch();
      if (seq == _seq) {
        _seq++; // supersede any in-flight load / loadMore
        _setFirstPage(p.content, !p.isLast);
        _error = null;
        _loading = false;
        notifyListeners();
      }
      return const Ok(null);
    } catch (e) {
      return Err(ApiException.from(e));
    }
  }

  void _setFirstPage(List<AppNotification> items, bool hasMore) {
    _items = items;
    _page = 0;
    _hasMore = hasMore;
    _loaded = true;
    _loadingMore = false;
    _loadMoreError = null;
  }

  /// Infinite scroll: appends the next page.
  Future<void> loadMore() async {
    if (!_loaded || !_hasMore || _loadingMore || _loading) return;
    final seq = _seq;
    _loadingMore = true;
    _loadMoreError = null;
    notifyListeners();
    try {
      final next = _page + 1;
      final p = await _fetch(page: next);
      if (seq != _seq) return; // list was reloaded meanwhile
      final seen = _items.map((e) => e.id).toSet();
      _items = [..._items, ...p.content.where((e) => !seen.contains(e.id))];
      _page = next;
      _hasMore = !p.isLast && p.content.isNotEmpty;
    } catch (e) {
      if (seq != _seq) return;
      _loadMoreError = ApiException.from(e);
    }
    _loadingMore = false;
    notifyListeners();
  }
}
