package com.icapps.background_location_tracker.flutter

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.icapps.background_location_tracker.utils.Logger
import com.icapps.background_location_tracker.utils.SharedPrefsUtil
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation

internal object FlutterBackgroundManager {
    private const val BACKGROUND_CHANNEL_NAME =
            "com.icapps.background_location_tracker/background_channel"

    private val flutterLoader = FlutterLoader()
    private var engine: FlutterEngine? = null
    private var backgroundChannel: MethodChannel? = null
    private var isInitialized = false

    private fun getInitializedFlutterEngine(ctx: Context): FlutterEngine {
        if (engine == null) {
            Logger.debug("BackgroundManager", "Creating new engine")
            engine = FlutterEngine(ctx.applicationContext)
        }
        return engine!!
    }

    fun sendLocation(ctx: Context, location: Location) {
        Logger.debug("BackgroundManager", "Location: ${location.latitude}: ${location.longitude}")
        val engine = getInitializedFlutterEngine(ctx)

        if (!isInitialized) {
            initialize(ctx, engine, location)
        }

        val data = mutableMapOf<String, Any>()
        data["lat"] = location.latitude
        data["lon"] = location.longitude
        data["alt"] = if (location.hasAltitude()) location.altitude else 0.0
        data["vertical_accuracy"] = -1.0
        data["horizontal_accuracy"] = if (location.hasAccuracy()) location.accuracy else -1.0
        data["course"] = if (location.hasBearing()) location.bearing else -1.0
        data["course_accuracy"] = -1.0
        data["speed"] = if (location.hasSpeed()) location.speed else -1.0
        data["speed_accuracy"] = -1.0
        data["logging_enabled"] = SharedPrefsUtil.isLoggingEnabled(ctx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            data["vertical_accuracy"] =
                    if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else -1.0
            data["course_accuracy"] =
                    if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees else -1.0
            data["speed_accuracy"] =
                    if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else -1.0
        }

        backgroundChannel?.invokeMethod(
                "onLocationUpdate",
                data,
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        Logger.debug("BackgroundManager", "Got success!")
                    }

                    override fun error(
                            errorCode: String,
                            errorMessage: String?,
                            errorDetails: Any?
                    ) {
                        Logger.debug(
                                "BackgroundManager",
                                "Got error! $errorCode - $errorMessage : $errorDetails"
                        )
                    }

                    override fun notImplemented() {
                        Logger.debug("BackgroundManager", "Got not implemented!")
                    }
                }
        )
    }

    private fun initialize(ctx: Context, engine: FlutterEngine, location: Location) {
        backgroundChannel = MethodChannel(engine.dartExecutor, BACKGROUND_CHANNEL_NAME)
        backgroundChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "initialized" -> handleInitialized(call, result, ctx, location)
                else -> {
                    result.notImplemented()
                }
            }
        }

        if (!flutterLoader.initialized()) {
            flutterLoader.startInitialization(ctx)
        }
        flutterLoader.ensureInitializationCompleteAsync(
                ctx,
                null,
                Handler(Looper.getMainLooper())
        ) {
            val callbackHandle = SharedPrefsUtil.getCallbackHandle(ctx)
            val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
            val dartBundlePath = flutterLoader.findAppBundlePath()
            engine.dartExecutor.executeDartCallback(
                    DartExecutor.DartCallback(ctx.assets, dartBundlePath, callbackInfo)
            )
        }

        isInitialized = true
    }

    private fun handleInitialized(
            call: MethodCall,
            result: MethodChannel.Result,
            ctx: Context,
            location: Location
    ) {
        val data = mutableMapOf<String, Any>()
        data["lat"] = location.latitude
        data["lon"] = location.longitude
        data["alt"] = if (location.hasAltitude()) location.altitude else 0.0
        data["vertical_accuracy"] = -1.0
        data["horizontal_accuracy"] = if (location.hasAccuracy()) location.accuracy else -1.0
        data["course"] = if (location.hasBearing()) location.bearing else -1.0
        data["course_accuracy"] = -1.0
        data["speed"] = if (location.hasSpeed()) location.speed else -1.0
        data["speed_accuracy"] = -1.0
        data["logging_enabled"] = SharedPrefsUtil.isLoggingEnabled(ctx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            data["vertical_accuracy"] =
                    if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else -1.0
            data["course_accuracy"] =
                    if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees else -1.0
            data["speed_accuracy"] =
                    if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else -1.0
        }

        backgroundChannel?.invokeMethod(
                "onLocationUpdate",
                data,
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        Logger.debug("BackgroundManager", "Got success!")
                    }

                    override fun error(
                            errorCode: String,
                            errorMessage: String?,
                            errorDetails: Any?
                    ) {
                        Logger.debug(
                                "BackgroundManager",
                                "Got error! $errorCode - $errorMessage : $errorDetails"
                        )
                    }

                    override fun notImplemented() {
                        Logger.debug("BackgroundManager", "Got not implemented!")
                    }
                }
        )
    }

    fun destroyEngine() {
        engine?.destroy()
        engine = null
        backgroundChannel = null
        isInitialized = false
    }
}