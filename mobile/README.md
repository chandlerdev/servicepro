# ServProWare (technician mobile app)

Flutter app for ServProWare field technicians (Android & iOS). It talks to the
backend's `/api/auth/**` and `/api/tech/**` endpoints (Swagger UI at http://localhost:9091/swagger-ui.html).

Tabs:
- **My Stops**: pick a day, see your stops, then navigate, call, mark *On my way*, report a *Delay*, or mark *Done*.
- **Map**: depot plus numbered stops and the route polyline, with a "Navigating to…" card and a Google Maps hand-off.
- **Alerts**: SMS/e-mail notifications sent to customers on your routes.
- **Profile**: today's stats, vehicle and sign out.

Only `TECHNICIAN` accounts can sign in. Admins are told to use the web panel.

## Requirements

- Flutter 3.38+ / Dart 3.10+
- Android SDK (API 24+) and/or Xcode 15+ with CocoaPods (iOS 15+)
- A running backend (default `http://localhost:9091` on your machine)

```bash
flutter pub get
```

## Running

The app always talks to the live server, `https://teksupportsquad.desss-portfolio.com`
(set in `lib/config/app_config.dart`). No extra flags are needed:

```bash
flutter run            # on a connected phone or emulator
```

Log in with a technician account (the admin sets it under Route Master → Set app login).

## Google Maps API keys

The app runs without a key. In that case the Map tab shows the stops in route order, each with an
"Open in Google Maps ↗" button. Nothing crashes on either platform: native code tells Flutter whether
a key is present (MethodChannel `servicepros/config`).

Create the keys in Google Cloud Console and enable **Maps SDK for Android** and **Maps SDK for iOS**.
Restrict each key to its platform and app ID (`com.servicepros.servicepros_tech`).

### Android
Add the key to `android/local.properties`. That file is git-ignored.
```properties
MAPS_API_KEY=AIza...your-android-key
```
You can export the `MAPS_API_KEY` environment variable instead, which suits CI.
`android/app/build.gradle.kts` puts the key into the manifest placeholder `${MAPS_API_KEY}`
(`com.google.android.geo.API_KEY`). After changing the key, rebuild with `flutter clean` (if needed) and then `flutter run`.

### iOS
```bash
cp ios/Flutter/MapsKey.xcconfig.example ios/Flutter/MapsKey.xcconfig   # git-ignored
# edit: GMS_API_KEY = AIza...your-ios-key
```
`Debug.xcconfig` and `Release.xcconfig` load this file with `#include?`. `Info.plist` maps it to the
`GMSApiKey` entry, and `AppDelegate.swift` passes that value to `GMSServices.provideAPIKey(...)`
at launch. If the value is empty, the map SDK is not started and the fallback list is shown.

## Building

```bash
# Android
flutter build apk --release        # → build/app/outputs/flutter-apk/app-release.apk
flutter build appbundle --release  # Play Store

# iOS (on macOS)
cd ios && pod install && cd ..
flutter build ipa --release
```
Release signing on Android still uses the debug key. Before publishing, set up a release
`signingConfig` in `android/app/build.gradle.kts`. For iOS, choose your team in Xcode
(Runner → Signing & Capabilities).

## Quality

```bash
flutter analyze
flutter test
```
Tests cover:
- time and date formatting, haversine distance and the Google Maps URL
- parsing of every API model
- `ApiClient` (with `package:http/testing.dart` `MockClient`): 401 → one shared refresh → retry, concurrent 401s
  sharing one refresh, session expiry when the refresh is rejected, login errors not intercepted,
  field errors, network/timeout messages, UTF-8 decoding
- view models with fake repositories: `StopsViewModel` (load / error / refresh / day change / per-card busy
  state / action results), `HomeViewModel`, `AlertsViewModel` paging

## Architecture

MVVM following Flutter's [app architecture guide](https://docs.flutter.dev/app-architecture):
**Views** (widgets) → **ViewModels** (`ChangeNotifier`) → **Repositories** → **Services**.
Dependencies are injected with `package:provider`; HTTP uses `package:http`.

```
lib/
  main.dart          MultiProvider: services → repositories → app-level view models
  app.dart           MaterialApp.router
  config/            live API URL + constants + native "maps configured?" check; theme/colours
  routing/           go_router; refreshListenable = AuthRepository; redirect splash / login / home;
                     route builders create the screen view models (ChangeNotifierProvider)
  data/
    services/        ApiClient (http.Client wrapper), ApiException, TokenStorage (flutter_secure_storage)
    models/          User/AuthResponse, TechStops/RoutePlan/Stop/CompleteResult, AppNotification,
                     TechProfile, PageResult<T>, tolerant JSON readers
    repositories/    AuthRepository (ChangeNotifier: session status/user/message), StopsRepository,
                     AlertsRepository, ProfileRepository
  ui/
    core/            ViewModel base class, shared widgets (StopNumber, StatusChip, error/empty views),
                     utils (formatters, geo, url_launcher helpers, snackbars)
    auth/            LoginViewModel + LoginScreen, SplashScreen
    home/            HomeViewModel (tab, selected day, map focus) + HomeShell (bottom nav, header,
                     app-lifecycle hooks for the 60 s auto-refresh)
    stops/           StopsViewModel (day's stops, refresh/auto-refresh, per-card busy state, actions)
                     + StopsScreen, StopCard, delay/complete dialogs
    map/             MapViewModel (stops + focus + NavLeg + maps-key check) + MapScreen,
                     canvas marker icons, NavBanner
    alerts/          AlertsViewModel (paging / infinite scroll) + AlertsScreen
    profile/         ProfileViewModel (profile, today's stats, sign out)
                     + ProfileScreen
  utils/result.dart  Result<T> = Ok | Err (returned by view-model commands)
```

Rules of the layers:
- **Services** do transport only. `ApiClient` wraps one injected `http.Client`: base URL, JSON
  encode/decode (always UTF-8), 20 s timeout, `Authorization: Bearer`, and error mapping to `ApiException`
  (API `message` + `fieldErrors`, or a friendly network/timeout message). On a 401 (except
  `/api/auth/login|refresh|logout`) it calls `/api/auth/refresh` once, with a single in-flight refresh shared
  by concurrent requests, stores the rotated tokens and retries the request once. If the server rejects the
  refresh token, the tokens are cleared and `ApiClient.sessionExpired` fires; `AuthRepository` then switches
  to *unauthenticated* and the router redirects to `/login` ("Your session has expired").
- **Repositories** are plain Dart classes (no widgets) that return models or throw `ApiException`.
  `AuthRepository` is the session's source of truth and a `ChangeNotifier` for the router.
  `StopsRepository.changes` fires after a stop action so Profile's "today" stats reload.
- **ViewModels** extend `ChangeNotifier`, hold UI state (loading / error / data / busy ids) and expose
  commands (`load`, `refresh`, `onMyWay`, `delay`, `complete`, `loadMore`, …). They never
  touch `BuildContext`; commands return `Result<T>` and the View shows snackbars/dialogs. Timers and
  listeners are released in `dispose()`.
- **Views** use `context.watch` / `context.select` / `context.read`.

Scoping: `HomeViewModel` is app-wide (it resets on every new sign-in). The stops, map, alerts and profile
view models are created in the home route's builder, so signing out disposes them and the next user starts
with empty caches. Tokens are kept in `flutter_secure_storage`.

Notes:
- "Today" is the device's local date. The backend's business timezone is America/Chicago.
- The map card's distance is a straight line (haversine) from the previous stop, or from the depot for the
  first stop. Drive time assumes 32 km/h.
- The app does not track location (no location permission in v1).
