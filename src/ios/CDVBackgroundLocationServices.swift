//
//  CDVLocationServices.swift
//  CordovaLib
//
//  Created by Paul Michael Wisdom on 5/31/15.
//
//

import Foundation
import CoreLocation
import CoreMotion


let TAG = "[LocationServices]";
let PLUGIN_VERSION = "1.0";

func log(message: String){
    if(debug == true) {
        NSLog("%@ - %@", TAG, message)
    }
}

var locationManager = LocationManager();
var activityManager = ActivityManager();
var taskManager = TaskManager();

//Option Vars
var distanceFilter = kCLDistanceFilterNone;
var desiredAccuracy = kCLLocationAccuracyBest;
var activityType = CLActivityType.AutomotiveNavigation;
var interval = 5.0;
var debug: Bool?;
var useActivityDetection = false;
var aggressiveInterval = 2.0;

var stationaryTimout = (Double)(5 * 60); // 5 minutes
var backgroundTaskCount = 0;

//State vars
var enabled = false;
var background = false;

var locationUpdateCallback:String?;
var locationCommandDelegate:CDVCommandDelegate?;

var activityUpdateCallback:String?;
var activityCommandDelegate:CDVCommandDelegate?;


@objc(HWPBackgroundLocationServices) class BackgroundLocationServices : CDVPlugin {

    //Initialize things here (basically on run)
    override func pluginInitialize() {
        super.pluginInitialize();

        locationManager.requestLocationPermissions();
        self.promptForNotificationPermission();

        NSNotificationCenter.defaultCenter().addObserver(
            self,
            selector: #selector(BackgroundLocationServices.onResume),
            name: UIApplicationWillEnterForegroundNotification,
            object: nil);

        NSNotificationCenter.defaultCenter().addObserver(
            self,
            selector: #selector(BackgroundLocationServices.onSuspend),
            name: UIApplicationDidEnterBackgroundNotification,
            object: nil);

        NSNotificationCenter.defaultCenter().addObserver(
            self,
            selector: #selector(BackgroundLocationServices.willResign),
            name: UIApplicationWillResignActiveNotification,
            object: nil);
    }

    // 0 distanceFilter,
    // 1 desiredAccuracy,
    // 2 interval,
    // 3 fastestInterval -- (not used on ios),
    // 4 aggressiveInterval,
    // 5 debug,
    // 6 notificationTitle -- (not used on ios),
    // 7 notificationText-- (not used on ios),
    // 8 activityType, fences -- (not used ios)
    // 9 useActivityDetection
    func configure(command: CDVInvokedUrlCommand) {

        //log("configure arguments: \(command.arguments)");

        distanceFilter = command.argumentAtIndex(0) as! CLLocationDistance;
        desiredAccuracy = self.toDesiredAccuracy((command.argumentAtIndex(1) as! Int));
        interval = (Double)(command.argumentAtIndex(2) as! Int / 1000); // Millseconds to seconds
        aggressiveInterval = (Double)(command.argumentAtIndex(4) as! Int / 1000); // Millseconds to seconds
        activityType = self.toActivityType(command.argumentAtIndex(8) as! String);
        debug = command.argumentAtIndex(5) as? Bool;
        useActivityDetection = command.argumentAtIndex(9) as! Bool;

        log("--------------------------------------------------------");
        log("   Configuration Success");
        log("       Distance Filter     \(distanceFilter)");
        log("       Desired Accuracy    \(desiredAccuracy)");
        log("       Activity Type       \(activityType)");
        log("       Update Interval     \(interval)");
        log("--------------------------------------------------------");

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
    }

    func registerForLocationUpdates(command: CDVInvokedUrlCommand) {
        log("registerForLocationUpdates");
        locationUpdateCallback = command.callbackId;
        locationCommandDelegate = commandDelegate;
    }

    func registerForActivityUpdates(command : CDVInvokedUrlCommand) {
        log("registerForActivityUpdates");
        activityUpdateCallback = command.callbackId;
        activityCommandDelegate = commandDelegate;
    }


    func start(command: CDVInvokedUrlCommand) {
        log("Started");
        enabled = true;

        log("Are we in the background? \(background)");

        if(background) {
            locationManager.startUpdating(false);
            activityManager.startDetection();
        }

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
    }

    func stop(command: CDVInvokedUrlCommand) {
        log("Stopped");
        enabled = false;

        locationManager.stopBackgroundTracking();
        activityManager.stopDetection();

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
    }

    func getVersion(command: CDVInvokedUrlCommand) {
        log("Returning Version \(PLUGIN_VERSION)");

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsString: PLUGIN_VERSION);
        commandDelegate!.sendPluginResult(pluginResult, callbackId: command.callbackId);
    }

    func startAggressiveTracking(command: CDVInvokedUrlCommand) {
        log("startAggressiveTracking");
        locationManager.startAggressiveTracking();

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsString: PLUGIN_VERSION);
        commandDelegate!.sendPluginResult(pluginResult, callbackId: command.callbackId);

    }

    func promptForNotificationPermission() {
        log("Prompting For Notification Permissions");
        if #available(iOS 8, *) {
            UIApplication.sharedApplication().registerUserNotificationSettings(UIUserNotificationSettings(
                forTypes: [UIUserNotificationType.Sound, UIUserNotificationType.Alert, UIUserNotificationType.Badge],
                categories: nil
                )
            )
        } else {
            UIApplication.sharedApplication().registerForRemoteNotificationTypes([UIRemoteNotificationType.Alert, UIRemoteNotificationType.Sound, UIRemoteNotificationType.Badge]);
        }
    }

    //State Methods
    func onResume() {
        log("App Resumed");
        background = false;

        taskManager.endAllBackgroundTasks();
        locationManager.stopUpdating();
        activityManager.stopDetection();
    }

    func onSuspend() {
        log("App Suspended. Enabled? \(enabled)");
        background = true;

        if(enabled) {
            locationManager.startUpdating(false);
            activityManager.startDetection();
        }
    }

    func willResign() {
        log("App Will Resign. Enabled? \(enabled)");
        background = true;

        if(enabled) {
            locationManager.startUpdating(false);
            activityManager.startDetection();
        }
    }

    /* Pinpoint our location with the following accuracy:
    *
    *     kCLLocationAccuracyBestForNavigation  highest + sensor data
    *     kCLLocationAccuracyBest               highest
    *     kCLLocationAccuracyNearestTenMeters   10 meters
    *     kCLLocationAccuracyHundredMeters      100 meters
    *     kCLLocationAccuracyKilometer          1000 meters
    *     kCLLocationAccuracyThreeKilometers    3000 meters
    */

    func toDesiredAccuracy(distance: Int) -> CLLocationAccuracy {
        if(distance == 0) {
            return kCLLocationAccuracyBestForNavigation;
        } else if(distance < 10) {
            return kCLLocationAccuracyBest;
        } else if(distance < 100) {
            return kCLLocationAccuracyNearestTenMeters;
        } else if (distance < 1000) {
            return kCLLocationAccuracyHundredMeters
        } else if (distance < 3000) {
            return kCLLocationAccuracyKilometer;
        } else {
            return kCLLocationAccuracyThreeKilometers;
        }
    }

    func toActivityType(type: String) -> CLActivityType {
        if(type == "AutomotiveNavigation") {
            return CLActivityType.AutomotiveNavigation;
        } else if(type == "OtherNavigation") {
            return CLActivityType.OtherNavigation;
        } else if(type == "Fitness") {
            return CLActivityType.Fitness;
        } else {
            return CLActivityType.AutomotiveNavigation;
        }
    }
}

class LocationManager : NSObject, CLLocationManagerDelegate {
    var manager = CLLocationManager();
    let SECS_OLD_MAX = 2.0;

    var locationArray = [CLLocation]();
    var updating = false;
    var aggressive = false;
    
    var lowPowerMode = false;

    override init() {
        super.init();

        if(self.manager.delegate == nil) {
            log("Setting location manager");
            self.manager.delegate = self;

            self.enableBackgroundLocationUpdates();

            self.manager.desiredAccuracy = desiredAccuracy;
            self.manager.distanceFilter = distanceFilter;
            self.manager.pausesLocationUpdatesAutomatically = false;
            self.manager.activityType = activityType;
        }
    }

    func enableBackgroundLocationUpdates() {
        // New property required for iOS 9 to get location updates in background:
        // http://stackoverflow.com/questions/30808192/allowsbackgroundlocationupdates-in-cllocationmanager-in-ios9
        if #available(iOS 9, *) {
            self.manager.allowsBackgroundLocationUpdates = true;
        }
    }

    func locationToDict(loc:CLLocation) -> NSDictionary {
        let locDict:Dictionary = [
            "latitude" : loc.coordinate.latitude,
            "longitude" : loc.coordinate.longitude,
            "accuracy" : loc.horizontalAccuracy,
            "timestamp" : ((loc.timestamp.timeIntervalSince1970 as Double) * 1000),
            "speed" : loc.speed,
            "altitude" : loc.altitude,
            "heading" : loc.course
        ]

        return locDict;
    }

    func stopBackgroundTracking() {
        taskManager.endAllBackgroundTasks();
        self.stopUpdating();
    }

    func sync() {
        log("sync called");
        self.enableBackgroundLocationUpdates();

        var bestLocation:CLLocation?;
        var bestAccuracy = 3000.00;

        if(locationArray.count == 0) {
            log("locationArray has no entries");
            return;
        }

        for loc in locationArray {
            if(bestLocation == nil) {
                bestAccuracy = loc.horizontalAccuracy;
                bestLocation = loc;
            } else if(loc.horizontalAccuracy < bestAccuracy) {
                bestAccuracy = loc.horizontalAccuracy;
                bestLocation = loc;
            } else if (loc.horizontalAccuracy == bestAccuracy) &&
                (loc.timestamp.compare(bestLocation!.timestamp) == NSComparisonResult.OrderedDescending) {
                    bestAccuracy = loc.horizontalAccuracy;
                    bestLocation = loc;
            }
        }

        log("bestLocation: {\(bestLocation)}");

        if bestLocation != nil {
            locationArray.removeAll(keepCapacity: false);

            let latitude = bestLocation!.coordinate.latitude;
            let longitude = bestLocation!.coordinate.longitude;
            let accuracy = bestLocation!.horizontalAccuracy;

            var msg = "Got Location Update:  { \(latitude) - \(longitude) }  Accuracy: \(accuracy)";
            
            if(useActivityDetection) {
                    msg += " Stationary : \(activityManager.isStationary)";
            }
            
            log(msg);
            NotificationManager.manager.notify(msg);

            locationCommandDelegate?.runInBackground({

                var result:CDVPluginResult?;
                let loc = self.locationToDict(bestLocation!) as [NSObject: AnyObject];

                result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary:loc);
                result!.setKeepCallbackAsBool(true);
                locationCommandDelegate?.sendPluginResult(result, callbackId:locationUpdateCallback);
            });
        }
    }

    func startAggressiveTracking() {
        log("Got Request To Start Aggressive Tracking");
        self.enableBackgroundLocationUpdates();
        self.aggressive = true;

        interval = 1;
        syncSeconds = 1;
        desiredAccuracy = kCLLocationAccuracyBest;
        distanceFilter = 0;
    }

    // Force here is to make sure we are only starting the location updates once, until we want to restart them
    // Was having issues with it starting, and then starting a second time through resign on some builds.
    func startUpdating(force : Bool) {
        if(!self.updating || force) {
            self.enableBackgroundLocationUpdates();
            self.updating = true;

            self.manager.delegate = self;

            self.manager.desiredAccuracy = self.lowPowerMode ? kCLLocationAccuracyThreeKilometers : desiredAccuracy;
            self.manager.distanceFilter = self.lowPowerMode ? 10.0 : distanceFilter;

            self.manager.startUpdatingLocation();
            self.manager.startMonitoringSignificantLocationChanges();

            taskManager.beginNewBackgroundTask();

            log("Starting Location Updates!");
        } else {
            log("A request was made to start Updating, but the plugin was already updating")
        }
    }

    func stopUpdating() {
        log("Stopping Location Updates!");
        self.updating = false;

        if(locationTimer != nil) {
            locationTimer.invalidate();
            locationTimer = nil;
        }

        self.manager.stopUpdatingLocation();
        self.manager.stopMonitoringSignificantLocationChanges();
    }

    func locationManager(manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let locationArray = locations as NSArray
        let locationObj = locationArray.lastObject as! CLLocation

        let eventDate = locationObj.timestamp;
        let timeSinceUpdate = eventDate.timeIntervalSinceNow as Double;

        //log("locationArray: \(locationArray)");
        //log("locationObj: \(locationObj)");
        //log("eventDate: \(eventDate)");
        //log("timeSinceUpdate: \(timeSinceUpdate)");
        //log("locationTimer: \(locationTimer)");

        //Check here to see if the location is cached
        if abs(timeSinceUpdate) < SECS_OLD_MAX {
            self.locationArray.append(locationObj);
        }

        if(locationTimer != nil) {
            return;
        }

        taskManager.beginNewBackgroundTask();

        locationTimer = NSTimer.scheduledTimerWithTimeInterval(activityManager.isStationary ? stationaryTimout : interval, target: self, selector: #selector(LocationManager.restartUpdates), userInfo: nil, repeats: false);

        if(stopUpdateTimer != nil) {
            stopUpdateTimer.invalidate();
            stopUpdateTimer = nil;
        }

        stopUpdateTimer = NSTimer.scheduledTimerWithTimeInterval(syncSeconds, target: self, selector: #selector(LocationManager.syncAfterXSeconds), userInfo: nil, repeats: false);
    }

    func restartUpdates() {
        log("restartUpdates called");
        if(locationTimer != nil) {
            locationTimer.invalidate();
            locationTimer = nil;
        }
        
        self.lowPowerMode = false;

        self.manager.delegate = self;
        self.manager.desiredAccuracy = desiredAccuracy;
        self.manager.distanceFilter = distanceFilter;

        self.startUpdating(true);
    }
    
    func setGPSLowPower() {
        log("Setting GPS To Low Power Mode ");
        self.lowPowerMode = true;
        self.startUpdating(true);
    }

    func syncAfterXSeconds() {
        self.setGPSLowPower();
        self.sync();
        log("Stopped Location Updates After \(syncSeconds)");
    }

    func locationManagerDidPauseLocationUpdates(manager: CLLocationManager) {
        log("Location Manager Paused Location Updates");
    }

    func locationManagerDidResumeLocationUpdates(manager: CLLocationManager) {
        log("Location Manager Resumed Location Updates");
    }

    func locationManager(manager: CLLocationManager, didFailWithError error: NSError) {
        log("LOCATION ERROR: \(error.description)");

        locationCommandDelegate?.runInBackground({

            var result:CDVPluginResult?;

            result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAsString: error.description);
            result!.setKeepCallbackAsBool(true);
            locationCommandDelegate?.sendPluginResult(result, callbackId:locationUpdateCallback);
        });


    }
    func locationManager(manager: CLLocationManager, didFinishDeferredUpdatesWithError error: NSError?) {
        log("Location Manager FAILED deferred \(error!.description)");
    }

    func requestLocationPermissions() {
        if (!CLLocationManager.locationServicesEnabled()) {
            log("Location services is not enabled");
        } else {
            log("Location services enabled");
        }
        if #available(iOS 8, *) {
            self.manager.requestAlwaysAuthorization();
        }
    }

}

class ActivityManager : NSObject {
    var manager : CMMotionActivityManager?;
    var available = false;
    var updating = false;
    
    var isStationary = false;
    
    override init() {
        super.init();
        
        if(CMMotionActivityManager.isActivityAvailable()) {
            log("Activity Manager is Available");
            
            self.manager = CMMotionActivityManager();
            self.available = true;
        } else {
            log("Activity Manager is not Available");
        }
    }
    
    func confidenceToInt(confidence : CMMotionActivityConfidence) -> Int {
        var confidenceMult = 0;
        
        switch(confidence) {
            case CMMotionActivityConfidence.High :
                confidenceMult = 100;
                break;
            case CMMotionActivityConfidence.Medium :
                confidenceMult = 50;
                break;
            case CMMotionActivityConfidence.Low :
                confidenceMult = 0;
                break;
            default:
                confidenceMult = 0;
                break;
        }
        
        return confidenceMult;
    }
    
    func getActivityConfidence(detectedActivity : Bool, multiplier : Int) -> Int {
        return (detectedActivity ? 1 : 0) * multiplier;
    }
    
    func activitiesToArray(data:CMMotionActivity) -> NSDictionary {
        let confidenceMult = self.confidenceToInt(data.confidence);
        
        var detectedActivities:Dictionary = [
            "UNKOWN" : self.getActivityConfidence(data.unknown, multiplier: confidenceMult),
            "STILL" : self.getActivityConfidence(data.stationary, multiplier: confidenceMult),
            "WALKING" : self.getActivityConfidence(data.walking, multiplier: confidenceMult),
            "RUNNING" : self.getActivityConfidence(data.running, multiplier: confidenceMult),
            "IN_VEHICLE" : self.getActivityConfidence(data.automotive, multiplier: confidenceMult)
        ];
        
        // Cycling Only available on IOS 8.0
        if #available(iOS 8.0, *) {
            detectedActivities["ON_BICYCLE"] = self.getActivityConfidence(data.cycling, multiplier: confidenceMult)
        }
        
        log("Received Detected Activities : \(detectedActivities)");
        
        return detectedActivities;
    }

    func sendActivitiesToCallback(activities : NSDictionary) {
        if(activityCommandDelegate != nil) {
            activityCommandDelegate?.runInBackground({
                
                var result:CDVPluginResult?;
                
                result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary:activities as [NSObject: AnyObject]);
                result!.setKeepCallbackAsBool(true);
                activityCommandDelegate?.sendPluginResult(result, callbackId:activityUpdateCallback);
            });
        }
    }
    
    func startDetection() {
        NSLog("Activity Manager - Starting Detection %@", self.available);
        if(useActivityDetection == false) {
            return;
        }
        
        if(self.available) {
            self.updating = true;
            
            manager!.startActivityUpdatesToQueue(NSOperationQueue()) { data in
                if let data = data {
                    dispatch_async(dispatch_get_main_queue(), {
                        if(data.stationary == true) {
                            self.isStationary = true;
                        } else {
                            if(self.isStationary == true) {
                                locationManager.restartUpdates();
                            }
                            
                            self.isStationary = false
                            
                        }
                        
                        self.sendActivitiesToCallback(self.activitiesToArray(data));
                        
                    })
                }
            }
        } else {
            log("Activity Manager - Not available on your device");
        }
    }
    
    func stopDetection() {
        if(self.available && self.updating) {
            self.updating = false;
            
            manager!.stopActivityUpdates();
        }
    }
}

var backgroundTimer: NSTimer!
var locationTimer: NSTimer!
var stopUpdateTimer: NSTimer!
var syncSeconds:NSTimeInterval = 2;


//Task Manager Singleton
class TaskManager : NSObject {

    let priority = DISPATCH_QUEUE_PRIORITY_HIGH;

    var _bgTaskList = [Int]();
    var _masterTaskId = UIBackgroundTaskInvalid;

    func beginNewBackgroundTask() -> UIBackgroundTaskIdentifier {
        //log("beginNewBackgroundTask called");

        let app = UIApplication.sharedApplication();

        var bgTaskId = UIBackgroundTaskInvalid;

        if(app.respondsToSelector("beginBackgroundTaskWithExpirationHandler")) {
            bgTaskId = app.beginBackgroundTaskWithExpirationHandler({
                log("Background task \(bgTaskId) expired");
            });
            if(self._masterTaskId == UIBackgroundTaskInvalid) {
                self._masterTaskId = bgTaskId;
                log("Started Master Task ID \(self._masterTaskId)");
            } else {
                log("Started Background Task \(bgTaskId)");
                self._bgTaskList.append(bgTaskId);
                self.endBackgroundTasks();
            }
        }

        return bgTaskId;
    }

    func endBackgroundTasks() {
        self.drainBGTaskList(false);
    }

    func endAllBackgroundTasks() {
        self.drainBGTaskList(true);
    }

    func drainBGTaskList(all:Bool){
        let app = UIApplication.sharedApplication();
        if(app.respondsToSelector("endBackgroundTask")) {
            let count = self._bgTaskList.count;

            for _ in 0 ..< count {
                let bgTaskId = self._bgTaskList[0] as Int;
                log("Ending Background Task  with ID \(bgTaskId)");
                app.endBackgroundTask(bgTaskId);
                self._bgTaskList.removeAtIndex(0);
            }

            if(self._bgTaskList.count > 0) {
                log("Background Task Still Active \(self._bgTaskList[0])");
            }

            if(all) {
                log("Killing Master Task \(self._masterTaskId)");
                app.endBackgroundTask(self._masterTaskId);
                self._masterTaskId = UIBackgroundTaskInvalid;
            } else {
                log("Kept Master Task ID \(self._masterTaskId)");
            }
        }

    }
}

class NotificationManager : NSObject {

    static var manager = NotificationManager();

    func notify(text: String) {
        if(debug == true) {
            log("Sending Notification");
            let notification = UILocalNotification();
            notification.timeZone = NSTimeZone.defaultTimeZone();
            notification.soundName = UILocalNotificationDefaultSoundName;
            notification.alertBody = text;

            UIApplication.sharedApplication().scheduleLocalNotification(notification);
        }
    }
}
