import '../models/tech_profile.dart';
import '../services/api_client.dart';

/// The signed-in technician's profile.
class ProfileRepository {
  ProfileRepository(this._api);

  final ApiClient _api;

  /// `GET /api/tech/me` → `{ user, technician, routes }`
  Future<TechProfile> me() async =>
      TechProfile.fromJson(jsonObject(await _api.get('/api/tech/me')));
}
