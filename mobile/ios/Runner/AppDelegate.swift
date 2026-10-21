import Flutter
import GoogleMaps
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {
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

    GeneratedPluginRegistrant.register(with: self)

    if let registrar = self.registrar(forPlugin: "ServiceProsConfig") {
      let channel = FlutterMethodChannel(
        name: "servicepros/config", binaryMessenger: registrar.messenger())
      channel.setMethodCallHandler { [weak self] call, result in
        switch call.method {
        case "isMapsConfigured":
          result(self?.mapsConfigured ?? false)
        default:
          result(FlutterMethodNotImplemented)
        }
      }
    }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
