import Flutter
import GoogleMaps
import UIKit

/// UIScene lifecycle (required by the current iOS SDK — apps without it are killed at launch):
/// Info.plist `UIApplicationSceneManifest` uses Flutter's `FlutterSceneDelegate`, and plugins and our
/// channel are registered when Flutter's engine is ready (`didInitializeImplicitFlutterEngine`).
/// See https://flutter.dev/to/uiscene-migration
@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  /// True when Info.plist `GMSApiKey` holds a real key (see README).
  private var mapsConfigured = false

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    if let key = Bundle.main.object(forInfoDictionaryKey: "GMSApiKey") as? String {
      let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
      if !trimmed.isEmpty && !trimmed.hasPrefix("$(") && trimmed != "YOUR_IOS_MAPS_API_KEY" {
        GMSServices.provideAPIKey(trimmed)
        mapsConfigured = true
      }
    }
    NSLog(mapsConfigured
      ? "ServProWare: Google Maps key found — Map tab shows the map"
      : "ServProWare: NO Google Maps key (GMS_API_KEY in ios/Flutter/MapsKey.xcconfig) — Map tab shows the stop list")
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)

    // Tells the Dart side whether a Maps key is set (AppConfig.isMapsConfigured).
    guard let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "ServiceProsConfig") else { return }
    let channel = FlutterMethodChannel(name: "servicepros/config", binaryMessenger: registrar.messenger())
    let configured = mapsConfigured
    channel.setMethodCallHandler { call, result in
      switch call.method {
      case "isMapsConfigured":
        result(configured)
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }
}
