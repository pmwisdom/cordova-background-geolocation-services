# cordova-background-location-services
Background Geolocation For Android and iOS

## Plugin is in BETA, I haven't found anything broken yet, but I wouldnt use this in production until its tested more! ##

#### What is this?
This plugin is for enabling background geolocation in your cordova project. It is NOT for long term geolocation (for instance tracking user over the course of a day without charge), it is for sub few hour intensive tracking or tracking while the device is charging. **It is currently not optimized for battery life.**

### Todo: 
 * Both: 
  * Reduce battery consumption via accelerometer readings
  * Enable better distance filtering, possibly by speed readings
  * Test
  * Documentation
 * Android:
  * Make Icon Notification user configureable
  * Find out how to enable callbacks for android even when the app is destroyed by the OS
 * iOS
  * Enable switching to only using significant changes and back 

### Techniques used:

**Android** : Uses a Android Service that runs seperately from your main application, that means it can techincally run forever, unless a user force closes the main app. Unfortunately this also means that to get location updates from the Android Service to a server, we have to HTTP.POST the locations. 

**iOS** : Uses timers to enable endless background tracking. Fortunately we can use regular javascript callbacks on iOS. Which means your main app will receive the location updates via registerForLocationUpdates, and then you can send those updates to your server via your preferred method in javascript.

### How to use: 

This plugin exports an object at 
````javascript
window.plugins.backgroundLocationServices
````

````javascript
var bgLocationServices =  window.plugins.backgroundLocationServices;

bgLocationServices.configure({
     desiredAccuracy: 1, // Desired Accuracy of the location updates (lower means more accurate but more battery consumption)
     distanceFilter: 1, // How far you must move from the last point to trigger a location update
     notificationTitle: 'BG Plugin', // <-- android only, customize the title of the notification
     notificationText: 'Tracking', // <-- android only, customize the text of the notification
     activityType: 'AutomotiveNavigation',
     debug: true, // <-- enable this hear sounds for background-geolocation life-cycle.
     interval: 9000, // Requested Interval in between location updates, in seconds
     fastestInterval: 5000, // <-- android only Fastest interval your app / server can handle updates
     url: 'http://192.168.1.48:3000/api/geolocation/positions/update', // <-- android only, Where locations will be posted to
     //Android only, optional set of paramaters that you want to be sent with each location update
     //same with headers
     params: {
      'Param1': 'Heres a param that gets sent to the server'
     },
     headers: {}
});

 //iOS ONLY, callback will fire each time a location is available
bgLocationServices.registerForLocationUpdates(function(location) {
     console.log("We got an iOS BG Update" + JSON.stringify(location));
}, function(err) {
     console.log("Error: Didnt get an update", err);
});

//Start the Background Tracker. When you enter the background tracking will start, and stop when you enter the foreground.
bgLocationServices.start();

````




