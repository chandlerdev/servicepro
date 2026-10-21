/// Spring-style page envelope:
/// `{ content, page, size, totalElements, totalPages }`.
class PageResult<T> {
  const PageResult({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  bool get isLast => page + 1 >= totalPages;

  factory PageResult.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) fromItem,
  ) {
    final raw = (json['content'] as List?) ?? const [];
    return PageResult(
      content: raw
          .whereType<Map>()
          .map((e) => fromItem(Map<String, dynamic>.from(e)))
          .toList(),
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? raw.length,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? raw.length,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
    );
  }
}
