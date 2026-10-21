package com.servicepros.servicepros_tech

import android.content.pm.PackageManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "servicepros/config")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isMapsConfigured" -> result.success(isMapsConfigured())
                    else -> result.notImplemented()
                }
            }
    }

    /** True when a real Google Maps key was injected via the MAPS_API_KEY placeholder. */
    private fun isMapsConfigured(): Boolean = try {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val key = info.metaData?.getString("com.google.android.geo.API_KEY")
        !key.isNullOrBlank() && key != "MISSING_MAPS_API_KEY"
    } catch (e: Exception) {
        false
    }
}
