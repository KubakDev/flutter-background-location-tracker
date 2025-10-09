package com.icapps.background_location_tracker

import android.content.Context
import com.icapps.background_location_tracker.flutter.FlutterLifecycleAdapter
import com.icapps.background_location_tracker.utils.ActivityCounter
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class BackgroundLocationTrackerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {
    private var lifecycle: androidx.lifecycle.Lifecycle? = null
    private var methodCallHelper: MethodCallHelper? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        registerBackgroundLocationManager(binding.binaryMessenger, binding.applicationContext)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        methodCallHelper?.handle(call, result)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding)
        if (methodCallHelper == null) {
            ActivityCounter.attach(binding.activity)
            methodCallHelper = MethodCallHelper.getInstance(binding.activity.applicationContext)
        }
        methodCallHelper?.let {
            lifecycle?.removeObserver(it)
            lifecycle?.addObserver(it)
        }
    }

    override fun onDetachedFromActivity() {}

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}

    override fun onDetachedFromActivityForConfigChanges() {}

    companion object {
        private const val FOREGROUND_CHANNEL_NAME = "com.icapps.background_location_tracker/foreground_channel"

        @JvmStatic
        private fun registerBackgroundLocationManager(messenger: BinaryMessenger, ctx: Context) {
            val channel = MethodChannel(messenger, FOREGROUND_CHANNEL_NAME)
            channel.setMethodCallHandler(BackgroundLocationTrackerPlugin().apply {
                if (methodCallHelper == null) {
                    methodCallHelper = MethodCallHelper.getInstance(ctx)
                }
                methodCallHelper?.let {
                    lifecycle?.removeObserver(it)
                    lifecycle?.addObserver(it)
                }
            })
        }
    }
}