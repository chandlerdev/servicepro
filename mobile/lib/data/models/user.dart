/// `User = { id, name, email, role: "SUPER_ADMIN"|"ADMIN"|"TECHNICIAN", technicianId, active }`
class User {
  const User({
    required this.id,
    required this.name,
    required this.email,
    required this.role,
    required this.technicianId,
    required this.active,
  });

  final int id;
  final String name;
  final String email;
  final String role;
  final int? technicianId;
  final bool active;

  bool get isTechnician => role == 'TECHNICIAN';
  bool get isAdmin => role == 'ADMIN';

  factory User.fromJson(Map<String, dynamic> json) => User(
    id: (json['id'] as num).toInt(),
    name: json['name'] as String? ?? '',
    email: json['email'] as String? ?? '',
    role: json['role'] as String? ?? '',
    technicianId: (json['technicianId'] as num?)?.toInt(),
    active: json['active'] as bool? ?? true,
  );
}

/// `AuthResponse = { accessToken, refreshToken, tokenType, expiresIn, user }`
class AuthResponse {
  const AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.tokenType,
    required this.expiresIn,
    required this.user,
  });

  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresIn;
  final User user;

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
    accessToken: json['accessToken'] as String,
    refreshToken: json['refreshToken'] as String,
    tokenType: json['tokenType'] as String? ?? 'Bearer',
    expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 0,
    user: User.fromJson(Map<String, dynamic>.from(json['user'] as Map)),
  );
}
