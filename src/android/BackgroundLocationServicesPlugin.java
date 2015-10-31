package com.flybuy.cordova.location;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.ComponentName;

public class BackgroundLocationServicesPlugin extends CordovaPlugin {
    private static final String TAG = "BackgroundLocationServicesPlugin";
    private static final String PLUGIN_VERSION = "1.0";

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";
    public static final String ACTION_SET_CONFIG = "setConfig";
    public static final String ACTION_AGGRESSIVE_TRACKING = "startAggressiveTracking";
    public static final String ACTION_GET_VERSION = "getVersion";
    public static final String ACTION_REGISTER_FOR_LOCATION_UPDATES = "registerForLocationUpdates";

    private Intent updateServiceIntent;
    private Intent geofenceServiceIntent;

    private Boolean isEnabled = false;
    private Boolean inBackground = false;
    private boolean isServiceBound = false;

    private String desiredAccuracy = "1000";

    private String interval = "300000";
    private String fastestInterval = "60000";
    private String aggressiveInterval = "4000";

    private String distanceFilter = "30";
    private String isDebugging = "false";
    private String notificationTitle = "Location Tracking";
    private String notificationText = "ENABLED";
    private String stopOnTerminate = "false";
    private String activityType = "Automotive";

    //Things I want to remove
    private String url;
    private String params;
    private String headers;

    private JSONArray fences = null;

    private CallbackContext locationUpdateCallback = null;

    private BroadcastReceiver receiver = null;

    // Used to (un)bind the service to with the activity
    private final ServiceConnection serviceConnection = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName name, IBinder binder) {
          // Nothing to do here
          Log.i(TAG, "SERVICE CONNECTED TO MAIN ACTIVITY");
      }

      @Override
      public void onServiceDisconnected(ComponentName name) {
        // Nothing to do here
        Log.i(TAG, "SERVICE DISCONNECTED");
      }
    };

    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) {

        Activity activity = this.cordova.getActivity();

        Boolean result = false;
        updateServiceIntent = new Intent(activity, BackgroundLocationUpdateService.class);

        if (ACTION_START.equalsIgnoreCase(action) && !isEnabled) {
            result = true;
            if (params == null || headers == null|| url == null) {
                callbackContext.error("Call configure before calling start");
            } else {
                callbackContext.success();
                updateServiceIntent.putExtra("desiredAccuracy", desiredAccuracy);
                updateServiceIntent.putExtra("distanceFilter", distanceFilter);
                updateServiceIntent.putExtra("desiredAccuracy", desiredAccuracy);
                updateServiceIntent.putExtra("isDebugging", isDebugging);
                updateServiceIntent.putExtra("notificationTitle", notificationTitle);
                updateServiceIntent.putExtra("notificationText", notificationText);
                updateServiceIntent.putExtra("interval", interval);
                updateServiceIntent.putExtra("fastestInterval", fastestInterval);
                updateServiceIntent.putExtra("aggressiveInterval", aggressiveInterval);

                //URL / PARAMS
                updateServiceIntent.putExtra("url", url);
                updateServiceIntent.putExtra("params", params);
                updateServiceIntent.putExtra("headers", headers);

                isServiceBound = bindServiceToWebview(activity, updateServiceIntent);

                isEnabled = true;
            }
        } else if (ACTION_STOP.equalsIgnoreCase(action)) {
            isEnabled = false;
            result = true;
            activity.stopService(updateServiceIntent);
            callbackContext.success();

            destroyLocationUpdateReceiver();
        } else if (ACTION_CONFIGURE.equalsIgnoreCase(action)) {
            result = true;
            try {
                // [distanceFilter, desiredAccuracy, interval, fastestInterval, aggressiveInterval, debug, notificationTitle, notificationText, activityType, fences, url, params, headers]
                //  0               1                2         3                4                   5      6                   7                8              9
                this.distanceFilter = data.getString(0);
                this.desiredAccuracy = data.getString(1);
                this.interval = data.getString(2);
                this.fastestInterval = data.getString(3);
                this.aggressiveInterval = data.getString(4);
                this.isDebugging = data.getString(5);
                this.notificationTitle = data.getString(6);
                this.notificationText = data.getString(7);
                this.activityType = data.getString(8);

                this.url = data.getString(10);
                Log.d(TAG, "URL" + this.url);
                this.params = data.getString(11);
                this.headers = data.getString(12);


            } catch (JSONException e) {
                Log.d(TAG, "Json Exception" + e);
                callbackContext.error("authToken/url required as parameters: " + e.getMessage());
            }
        } else if (ACTION_SET_CONFIG.equalsIgnoreCase(action)) {
            result = true;
            // TODO reconfigure Service
            callbackContext.success();
        } else if(ACTION_GET_VERSION.equalsIgnoreCase(action)) {
            result = true;
            callbackContext.success(PLUGIN_VERSION);
        } else if(ACTION_REGISTER_FOR_LOCATION_UPDATES.equalsIgnoreCase(action)) {
            result = true;
            // if(debug()) {
            //     Log.w(TAG, "WARNING: Anroid does not support callbacks yet. Use the HTTP configuration");
            // }
            //Register the funcition for repeated location update
            locationUpdateCallback = callbackContext;

            // callbackContext.error("Anroid does not support callbacks yet. Use the HTTP configuration");
        } else if(ACTION_AGGRESSIVE_TRACKING.equalsIgnoreCase(action)) {
            result = true;
            if(isEnabled) {
                this.cordova.getActivity().sendBroadcast(new Intent(Constants.CHANGE_AGGRESSIVE));
                callbackContext.success();
            } else {
                callbackContext.error("Tracking not enabled, need to start tracking before starting aggressive tracking");
            }
        }

        return result;
    }

    public Boolean debug() {
        if(Boolean.parseBoolean(isDebugging)) {
            return true;
        } else {
            return false;
        }
    }

    private Boolean bindServiceToWebview(Context context, Intent intent) {
      Boolean didBind = false;

      try {
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        context.startService(intent);

        createLocationUpdateReceiver();
        webView.getContext().registerReceiver(this.receiver, new IntentFilter(Constants.CALLBACK_LOCATION_UPDATE));

        didBind = true;
      } catch(Exception e) {
        Log.e(TAG, "ERROR BINDING SERVICE" + e);
      }

      return didBind;
    }

    @Override
    public void onPause(boolean multitasking) {
        if(debug()) {
            Log.d(TAG, "- locationUpdateReceiver Paused (starting recording = " + String.valueOf(isEnabled) + ")");
        }
        if (isEnabled) {
            Activity activity = this.cordova.getActivity();
            activity.sendBroadcast(new Intent(Constants.START_RECORDING));
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        if(debug()) {
            Log.d(TAG, "- locationUpdateReceiver Resumed (stopping recording)" + String.valueOf(isEnabled));
        }
        if (isEnabled) {
            Activity activity = this.cordova.getActivity();
            activity.sendBroadcast(new Intent(Constants.STOP_RECORDING));
        }
    }

    private void createLocationUpdateReceiver() {
        this.receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, final Intent intent) {
                if(debug()) {
                    Log.d(TAG, "Location Received, ready for callback");
                }
                if (locationUpdateCallback != null) {

                    if(debug()) {
                      Toast.makeText(context, "We recieveived a location update", Toast.LENGTH_SHORT).show();
                    }

                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            if(intent.getExtras() == null) {
                                locationUpdateCallback.error("ERROR: Location Was Null");
                            }

                            JSONObject data = locationToJSON(intent.getExtras());
                            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
                            pluginResult.setKeepCallback(true);
                            locationUpdateCallback.sendPluginResult(pluginResult);
                        }
                    });
                } else {
                    if(debug()) {
                      Toast.makeText(context, "We recieveived a location update but locationUpdate was null", Toast.LENGTH_SHORT).show();
                    }
                    Log.w(TAG, "WARNING LOCATION UPDATE CALLBACK IS NULL, PLEASE RUN REGISTER LOCATION UPDATES");
                }
            }
        };
    }

    private void destroyLocationUpdateReceiver() {
        if (this.receiver != null) {
            try {
                webView.getContext().unregisterReceiver(this.receiver);
                this.receiver = null;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering location receiver: ", e);
            }
        }
    }

    private JSONObject locationToJSON(Bundle b) {
        JSONObject data = new JSONObject();
        try {
            data.put("latitude", b.getDouble("latitude"));
            data.put("longitude", b.getDouble("longitude"));
            data.put("accuracy", b.getDouble("accuracy"));
            data.put("altitude", b.getDouble("altitude"));
            data.put("timestamp", b.getDouble("timestamp"));
            data.put("speed", b.getDouble("speed"));
            data.put("heading", b.getDouble("heading"));
        } catch(JSONException e) {
            Log.d(TAG, "ERROR CREATING JSON" + e);
        }

        return data;
    }


    /**
     * Override method in CordovaPlugin.
     * Checks to see if it should turn off
     */
    public void onDestroy() {
        Activity activity = this.cordova.getActivity();

        if(isEnabled && stopOnTerminate.equalsIgnoreCase("true")) {
            activity.stopService(updateServiceIntent);
        }
    }
}
