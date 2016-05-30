//
//  CDVLocationServices.swift
//  CordovaLib
//
//  Created by Paul Michael Wisdom on 5/31/15.
//
//

import Foundation
import CoreLocation

let TAG = "[LocationServices]";
let PLUGIN_VERSION = "1.0";

func log(message: String){
    if(debug == true) {
        NSLog("%@ - %@", TAG, message)
    }
}

var locationManager = LocationManager();
var taskManager = TaskManager();

//Option Vars
var distanceFilter = kCLDistanceFilterNone;
var desiredAccuracy = kCLLocationAccuracyBest;
var activityType = CLActivityType.AutomotiveNavigation;
var interval = 5.0;
var aggressiveInterval = 2.0;
var backgroundTaskCount = 0;
var debug: Bool?;

//State vars
var enabled = false;
var background = false;

var locationUpdateCallback:String?;
var locationCommandDelegate:CDVCommandDelegate?;


@objc(HWPBackgroundLocationServices) class BackgroundLocationServices : CDVPlugin {

    //Initialize things here (basically on run)
    override func pluginInitialize() {
        super.pluginInitialize();

        locationManager.requestLocationPermissions();
        self.promptForNotificationPermission();

        NSNotificationCenter.defaultCenter().addObserver(
            self,
            selector: "onResume",
            name: UIApplicationWillEnterForegroundNotification,
            object: nil);

        NSNotificationCenter.defaultCenter().addObserver(
            self,
            selector: "onSuspend",
            name: UIApplicationDidEnterBackgroundNotification,
            object: nil);

        NSNotificationCenter.defaultCenter().addObserver(
            self,
            selector: "willResign",
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
    func configure(command: CDVInvokedUrlCommand) {

        //log("configure arguments: \(command.arguments)");

        distanceFilter = command.argumentAtIndex(0) as! CLLocationDistance;
        desiredAccuracy = self.toDesiredAccuracy((command.argumentAtIndex(1) as! Int));
        interval = (Double)(command.argumentAtIndex(2) as! Int / 1000); // Millseconds to seconds
        aggressiveInterval = (Double)(command.argumentAtIndex(4) as! Int / 1000); // Millseconds to seconds
        activityType = self.toActivityType(command.argumentAtIndex(8) as! String);
        debug = command.argumentAtIndex(5) as? Bool;

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
        log("Activity Updates not enabled on IOS");

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAsString: "Activity Updates not enabled on IOS");
        commandDelegate!.sendPluginResult(pluginResult, callbackId: command.callbackId);
    }


    func start(command: CDVInvokedUrlCommand) {
        log("Started");
        enabled = true;
        
        log("Are we in the background? \(background)");
        
        if(background) {
            locationManager.startUpdating();
        }

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate!.sendPluginResult(pluginResult, callbackId:command.callbackId)
    }

    func stop(command: CDVInvokedUrlCommand) {
        log("Stopped");
        enabled = false;

        locationManager.stopBackgroundTracking();

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
    }

    func onSuspend() {
        log("App Suspended. Enabled? \(enabled)");
        background = true;

        if(enabled) {
            locationManager.startUpdating();
        }
    }

    func willResign() {
        log("App Will Resign. Enabled? \(enabled)");
        background = true;

        if(enabled) {
            locationManager.startUpdating();
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

            let msg = "Got Location Update:  { \(latitude) - \(longitude) }  Accuracy: \(accuracy)";
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

    func startUpdating() {
        self.enableBackgroundLocationUpdates();
        self.updating = true;

        self.manager.delegate = self;
        self.manager.desiredAccuracy = desiredAccuracy;
        self.manager.distanceFilter = distanceFilter;

        self.manager.startUpdatingLocation();
        self.manager.startMonitoringSignificantLocationChanges();

        taskManager.beginNewBackgroundTask();

        log("Starting Location Updates!");
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
        log("Got location update");
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

        locationTimer = NSTimer.scheduledTimerWithTimeInterval(interval, target: self, selector: Selector("restartUpdates"), userInfo: nil, repeats: false);

        if(stopUpdateTimer != nil) {
            stopUpdateTimer.invalidate();
            stopUpdateTimer = nil;
        }

        stopUpdateTimer = NSTimer.scheduledTimerWithTimeInterval(syncSeconds, target: self, selector: Selector("syncAfterXSeconds"), userInfo: nil, repeats: false);
    }

    func restartUpdates() {
        log("restartUpdates called");
        if(locationTimer != nil) {
            locationTimer.invalidate();
            locationTimer = nil;
        }

        self.manager.delegate = self;
        self.manager.desiredAccuracy = desiredAccuracy;
        self.manager.distanceFilter = distanceFilter;

        self.startUpdating();
    }

    func syncAfterXSeconds() {
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

            for var i = 0; i < count; i++ {
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