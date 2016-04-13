var exec = require("cordova/exec");
module.exports = {
    pName : 'BackgroundLocationServices',
    config: {},
    configure: function(config) {
        this.config = config;
        var distanceFilter      = (config.distanceFilter   >= 0) ? config.distanceFilter   : 500, // meters
          desiredAccuracy     = (config.desiredAccuracy  >= 0) ? config.desiredAccuracy  : 100, // meters
          interval            = (config.interval         >= 0) ? config.interval        : 900000, // milliseconds
          fastestInterval     = (config.fastestInterval  >= 0) ? config.fastestInterval : 120000, // milliseconds
          aggressiveInterval  = (config.aggressiveInterval > 0) ? config.aggressiveInterval : 4000, //mulliseconds
          debug               = config.debug || false,
          notificationTitle   = config.notificationTitle || "Background tracking",
          notificationText    = config.notificationText  || "ENABLED",
          activityType        = config.activityType || "AutomotiveNavigation",
          useActivityDetection = config.useActivityDetection || "false",
          activitiesInterval =  config.activitiesInterval || 1000;

        exec(function() {},
          function() {},
          'BackgroundLocationServices',
          'configure',
          [distanceFilter, desiredAccuracy,  interval, fastestInterval, aggressiveInterval, debug, notificationTitle, notificationText, activityType, useActivityDetection, activitiesInterval]
        );
    },
    registerForLocationUpdates : function(success, failure, config) {
        exec(success || function() {},
          failure || function() {},
          'BackgroundLocationServices',
          'registerForLocationUpdates',
          []
        );
    },
    registerForActivityUpdates : function(success, failure, config) {
        exec(success || function() {},
          failure || function() {},
          'BackgroundLocationServices',
          'registerForActivityUpdates',
          []
        );
    },
    start: function(success, failure, config) {
        exec(success || function() {},
          failure || function() {},
          'BackgroundLocationServices',
          'start',
          []);
    },
    startAggressiveTracking: function(success, failure) {
        exec(success || function() {},
          failure || function() {},
          'BackgroundLocationServices',
          'startAggressiveTracking',
          []);
    },
    stop: function(success, failure, config) {
        exec(success || function() {},
          failure || function() {},
          'BackgroundLocationServices',
          'stop',
          []);
    },
    getVersion: function (success, failure) {
        exec(success || function() {},
          failure || function() {},
          'BackgroundLocationServices',
          'getVersion',
          []);
    }
};
