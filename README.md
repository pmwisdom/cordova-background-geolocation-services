# cordova-background-geolocation-services
Background Geolocation For Android and iOS with pure javascript callbacks.

#### What is this?
This plugin is for enabling background geolocation in your cordova project. It was aimed with the specific goal of normalizing the API for android and iOS and retrieving constant location updates in the background until you tell it to stop (If you tell it you want updates every 3 seconds it will give you one every 3 seconds). This is currently in active development. Feel free to make any requests. 

I've also included an activity detection API. It is used to save battery life, but you can also retrieve the likelihood of what the user is currently doing (standing still, walking, running, driving, etc).

### Changelog :
 * 1.0.3 Activity Detection And Much Better Battery Life For iOS!
 * 1.0.2 Error callbacks now correctly funnel through the location register
 
### Techniques used:

**Android** : Uses Fused Location API and Activity Recognition API to serve location updates endlessly.

**iOS** : Uses a timer based approach and CoreMotion library to enable endless background tracking.

###Setup: 
* Make sure you have Google Play Services AND Google Repository installed via your android-sdk manager prior to building your application with this. It will be under the extras part of the sdk manager. More information can be found here: http://developer.android.com/sdk/installing/adding-packages.html.

###Installation:

Cordova :
````
cordova plugin add https://github.com/pmwisdom/cordova-background-geolocation-services.git
````

Meteor : 
````
meteor add mirrorcell:background-geolocation-plus
````

### How to use: 

This plugin exports an object at 
````javascript
window.plugins.backgroundLocationServices
````

````javascript

//Make sure to get at least one GPS coordinate in the foreground before starting background services
navigator.geolocation.getCurrentPosition(function() {
 console.log("Succesfully retreived our GPS position, we can now start our background tracker.");
}, function(error) {
 console.error(error);
});

//Get plugin
var bgLocationServices =  window.plugins.backgroundLocationServices;

//Congfigure Plugin
bgLocationServices.configure({
     //Both
     desiredAccuracy: 20, // Desired Accuracy of the location updates (lower means more accurate but more battery consumption)
     distanceFilter: 5, // (Meters) How far you must move from the last point to trigger a location update
     debug: true, // <-- Enable to show visual indications when you receive a background location update
     interval: 9000, // (Milliseconds) Requested Interval in between location updates.
     useActivityDetection: true, // Uses Activitiy detection to shut off gps when you are still (Greatly enhances Battery Life)
     
     //Android Only
     notificationTitle: 'BG Plugin', // customize the title of the notification
     notificationText: 'Tracking', //customize the text of the notification
     fastestInterval: 5000 // <-- (Milliseconds) Fastest interval your app / server can handle updates
     
});

//Register a callback for location updates, this is where location objects will be sent in the background
bgLocationServices.registerForLocationUpdates(function(location) {
     console.log("We got an BG Update" + JSON.stringify(location));
}, function(err) {
     console.log("Error: Didnt get an update", err);
});

//Register for Activity Updates

//Uses the Detected Activies / CoreMotion API to send back an array of activities and their confidence levels
//See here for more information:
//https://developers.google.com/android/reference/com/google/android/gms/location/DetectedActivity
bgLocationServices.registerForActivityUpdates(function(activities) {
     console.log("We got an activity update" + activities);
}, function(err) {
     console.log("Error: Something went wrong", err);
});

//Start the Background Tracker. When you enter the background tracking will start, and stop when you enter the foreground.
bgLocationServices.start();


///later, to stop
bgLocationServices.stop();
````

### Known Issues:

Phonegap Build : Swift files are not officially supported as of yet on phonegap build, so if there is a problem installing it in that environment, there isn't anything I can do until they are supported.

### Credit!

By the way, credit to Christocracy and his great [plugin](https://github.com/christocracy/cordova-plugin-background-geolocation/tree/0.3.7) that spurned this one. It should share the same concepts via javascript.

