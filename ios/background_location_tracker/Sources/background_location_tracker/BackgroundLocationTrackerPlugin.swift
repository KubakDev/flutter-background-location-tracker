import Flutter
import UIKit
import CoreLocation

public class BackgroundLocationTrackerPlugin: FlutterPluginAppLifeCycleDelegate {
    
    static let identifier = "com.icapps.background_location_tracker"
    
    private static let flutterThreadLabelPrefix = "\(identifier).BackgroundLocationTracker"
    
    private static var foregroundChannel: ForegroundChannel? = nil
    private static var backgroundMethodChannel: FlutterMethodChannel? = nil
    
    private static var flutterEngine: FlutterEngine? = nil
    private static var hasRegisteredPlugins = false
    private static var initializedBackgroundCallbacks = false
    private static var initializedBackgroundCallbacksStarted = false
    private static var locationData: [String: Any]? = nil
    
    private static var flutterPluginRegistrantCallback: FlutterPluginRegistrantCallback?
    
    private let locationManager = LocationManager.shared()
    
}

extension BackgroundLocationTrackerPlugin: FlutterPlugin {
    
    @objc
    public static func setPluginRegistrantCallback(_ callback: @escaping FlutterPluginRegistrantCallback) {
        flutterPluginRegistrantCallback = callback
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        foregroundChannel = ForegroundChannel()
        let methodChannel = ForegroundChannel.getMethodChannel(with: registrar)
        let instance = BackgroundLocationTrackerPlugin()
        registrar.addMethodCallDelegate(instance, channel: methodChannel)
        registrar.addApplicationDelegate(instance)
        
        // Ensure correct location permissions
        LocationManager.ensureCorrectPermissions()
        
        if (SharedPrefsUtil.isTracking() && SharedPrefsUtil.restartAfterKill()) {
            instance.locationManager.delegate = instance
            instance.locationManager.startUpdatingLocation()
        }
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        locationManager.delegate = self
        BackgroundLocationTrackerPlugin.foregroundChannel?.handle(call, result: result)
    }
    
    public static func getFlutterEngine()-> FlutterEngine? {
        if flutterEngine == nil {
            let flutterEngine = FlutterEngine(name: flutterThreadLabelPrefix, project: nil, allowHeadlessExecution: true)
            
            guard let callbackHandle = SharedPrefsUtil.getCallbackHandle(),
                  let flutterCallbackInformation = FlutterCallbackCache.lookupCallbackInformation(callbackHandle) else {
                CustomLogger.log(message: "No flutter callback cache ...")
                return nil
            }
            let success = flutterEngine.run(withEntrypoint: flutterCallbackInformation.callbackName, libraryURI: flutterCallbackInformation.callbackLibraryPath)
            
            CustomLogger.log(message: "FlutterEngine.run returned `\(success)`")
            if success {
                BackgroundLocationTrackerPlugin.flutterPluginRegistrantCallback?(flutterEngine)
                self.flutterEngine = flutterEngine
            } else {
                CustomLogger.log(message: "FlutterEngine.run returned `false` we will cleanup the flutterEngine")
                flutterEngine.destroyContext()
            }
        }
        return flutterEngine
    }
    
    public static func initBackgroundMethodChannel(flutterEngine: FlutterEngine) {
        if backgroundMethodChannel == nil {
            let backgroundMethodChannel = FlutterMethodChannel(name: BackgroundLocationTrackerPlugin.BACKGROUND_CHANNEL_NAME, binaryMessenger: flutterEngine.binaryMessenger)
            backgroundMethodChannel.setMethodCallHandler { (call, result) in
                switch call.method {
                case BackgroundMethods.initialized.rawValue:
                    initializedBackgroundCallbacks = true
                    if let data = BackgroundLocationTrackerPlugin.locationData {
                        CustomLogger.log(message: "Initialized with cached value, sending location update")
                        sendLocationupdate(locationData: data)
                    } else {
                        CustomLogger.log(message: "Initialized without cached value")
                    }
                    result(true)
                default:
                    CustomLogger.log(message: "Not implemented method -> \(call.method)")
                    result(FlutterMethodNotImplemented)
                }
            }
            self.backgroundMethodChannel = backgroundMethodChannel
        }
    }
    
    public static func sendLocationupdate(locationData: [String: Any]){
        guard let backgroundMethodChannel = BackgroundLocationTrackerPlugin.backgroundMethodChannel else {
            CustomLogger.log(message: "No background channel available ...")
            return
        }
        backgroundMethodChannel.invokeMethod(BackgroundMethods.onLocationUpdate.rawValue, arguments: locationData, result: { flutterResult in
            CustomLogger.log(message: "Received result: \(flutterResult.debugDescription)")
        })
    }
}

fileprivate enum BackgroundMethods: String {
    case initialized = "initialized"
    case onLocationUpdate = "onLocationUpdate"
}

extension BackgroundLocationTrackerPlugin: CLLocationManagerDelegate {
    private static let BACKGROUND_CHANNEL_NAME = "com.icapps.background_location_tracker/background_channel"
    
    // iOS 14+ authorization change handler
    @available(iOS 14.0, *)
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            if SharedPrefsUtil.isTracking() {
                CustomLogger.log(message: "Authorization granted, resuming location tracking")
                manager.startUpdatingLocation()
            }
        } else {
            CustomLogger.log(message: "Location authorization status changed: \(manager.authorizationStatus.rawValue)")
        }
    }
    
    // Pre-iOS 14 authorization change handler
    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            if SharedPrefsUtil.isTracking() {
                CustomLogger.log(message: "Authorization granted, resuming location tracking")
                manager.startUpdatingLocation()
            }
        } else {
            CustomLogger.log(message: "Location authorization status changed: \(status.rawValue)")
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else {
            CustomLogger.log(message: "No location ...")
            return
        }
        
        CustomLogger.log(message: "NEW LOCATION: \(location.coordinate.latitude): \(location.coordinate.longitude)")
        
        var locationData: [String: Any] = [
            "lat": location.coordinate.latitude,
            "lon": location.coordinate.longitude,
            "alt": location.altitude,
            "vertical_accuracy": location.verticalAccuracy,
            "horizontal_accuracy": location.horizontalAccuracy,
            "course": location.course >= 0 ? location.course : (manager.heading?.trueHeading ?? -1),
            "course_accuracy": -1,
            "speed": location.speed,
            "speed_accuracy": location.speedAccuracy,
            "logging_enabled": SharedPrefsUtil.isLoggingEnabled(),
        ]
        
        if #available(iOS 13.4, *) {
            locationData["course_accuracy"] = location.courseAccuracy
        }
        
        if BackgroundLocationTrackerPlugin.initializedBackgroundCallbacks {
            CustomLogger.log(message: "INITIALIZED, ready to send location updates")
            BackgroundLocationTrackerPlugin.sendLocationupdate(locationData: locationData)
        } else {
            CustomLogger.log(message: "NOT YET INITIALIZED. Cache the location data")
            BackgroundLocationTrackerPlugin.locationData = locationData
            
            if !BackgroundLocationTrackerPlugin.initializedBackgroundCallbacksStarted {
                BackgroundLocationTrackerPlugin.initializedBackgroundCallbacksStarted = true
            
                guard let flutterEngine = BackgroundLocationTrackerPlugin.getFlutterEngine() else {
                    CustomLogger.log(message: "No Flutter engine available ...")
                    return
                }
                BackgroundLocationTrackerPlugin.initBackgroundMethodChannel(flutterEngine: flutterEngine)
            }
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        // Only log if heading accuracy is good (lower values are better)
        if newHeading.headingAccuracy <= 15 {
            CustomLogger.log(message: "NEW HEADING: \(String(format: "%.1f", newHeading.trueHeading))")
        }
    }
}
