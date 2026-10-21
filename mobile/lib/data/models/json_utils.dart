/// Small, tolerant JSON readers used by manual `fromJson` factories.
double? jDouble(Object? v) => switch (v) {
  num n => n.toDouble(),
  String s => double.tryParse(s),
  _ => null,
};

int? jInt(Object? v) => switch (v) {
  num n => n.toInt(),
  String s => int.tryParse(s),
  _ => null,
};

String? jString(Object? v) => v?.toString();

Map<String, dynamic> jMap(Object? v) =>
    v is Map ? Map<String, dynamic>.from(v) : <String, dynamic>{};

List<Map<String, dynamic>> jList(Object? v) => v is List
    ? v.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList()
    : const [];
