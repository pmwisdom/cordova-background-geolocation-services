# cordova-background-geolocation-services
Background Geolocation For Android and iOS

#### What is this?
This plugin is for enabling background geolocation in your cordova project. It was aimed with the specific goal of normalizing the API for android and iOS and retrieving constant location updates in the background until you tell it to stop (If you tell it you want updates every 3 seconds it will give you one every 3 seconds). It is not optimized for battery life, yet. This is currently in active development. Feel free to make any requests. Below are features I am currently working on.

### Todo: 
 * Both: 
  * Reduce battery consumption via accelerometer readings
  * SQLlite Storage
  * Enable better distance filtering, possibly by speed readings
  * Test
  * Documentation
  * Demos (Regular Node, Meteor)
  * Meteor Specific Atmosphere Package
  * Npm
 * Android:
  * ~~Make Icon Notification user configureable~~
  * ~~Get Android callbacks to work, always.~~
 * iOS
  * Enable switching to only using significant changes and back 

### Techniques used:

**Android** : Uses an android Service and some trickery to bind it to your main App.

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
});

bgLocationServices.registerForLocationUpdates(function(location) {
     console.log("We got an BG Update" + JSON.stringify(location));
}, function(err) {
     console.log("Error: Didnt get an update", err);
});

//Start the Background Tracker. When you enter the background tracking will start, and stop when you enter the foreground.
bgLocationServices.start();

````
