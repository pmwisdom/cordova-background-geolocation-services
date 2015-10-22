# cordova-background-location-services
Background Geolocation For Android and iOS

## ALPHA BUILD, DO NOT USE IN PRODUCTION YET. ##

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


