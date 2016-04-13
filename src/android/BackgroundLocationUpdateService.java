package com.flybuy.cordova.location;

import java.util.List;
import java.util.Iterator;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;

import android.media.AudioManager;
import android.media.ToneGenerator;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import static android.telephony.PhoneStateListener.*;
import android.telephony.CellLocation;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Activity;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.location.Location;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;

import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import static java.lang.Math.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.content.res.Resources;


import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.ActivityRecognitionResult;
import java.util.ArrayList;
import com.google.android.gms.common.ConnectionResult;

//Detected Activities imports

public class BackgroundLocationUpdateService
        extends Service
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "BackgroundLocationUpdateService";

    private Location lastLocation;
    private DetectedActivity lastActivity;
    private long lastUpdateTime = 0l;
    private Boolean fastestSpeed = false;

    private PendingIntent locationUpdatePI;
    private GoogleApiClient locationClientAPI;
    private PendingIntent detectedActivitiesPI;
    private GoogleApiClient detectedActivitiesAPI;

    private Integer desiredAccuracy = 100;
    private Integer distanceFilter  = 30;

    private Integer activitiesInterval = 1000;

    private static final Integer SECONDS_PER_MINUTE      = 60;
    private static final Integer MILLISECONDS_PER_SECOND = 60;

    private long  interval             = (long)  SECONDS_PER_MINUTE * MILLISECONDS_PER_SECOND * 5;
    private long  fastestInterval      = (long)  SECONDS_PER_MINUTE * MILLISECONDS_PER_SECOND;
    private long  aggressiveInterval   = (long) MILLISECONDS_PER_SECOND * 4;

    private Boolean isDebugging;
    private String notificationTitle = "Background checking";
    private String notificationText = "ENABLED";
    private Boolean useActivityDetection = false;

    private Boolean stopOnTerminate;
    private Boolean isRequestingActivity = false;
    private Boolean isRecording = false;

    private ToneGenerator toneGenerator;

    private Criteria criteria;

    private ConnectivityManager connectivityManager;
    private NotificationManager notificationManager;

    private LocationRequest locationRequest;


    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        Log.i(TAG, "OnBind" + intent);

        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "OnCreate");

        toneGenerator           = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        notificationManager     = (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
        connectivityManager     = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        // Location Update PI
        Intent locationUpdateIntent = new Intent(Constants.LOCATION_UPDATE);
        locationUpdatePI = PendingIntent.getBroadcast(this, 9001, locationUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        registerReceiver(locationUpdateReceiver, new IntentFilter(Constants.LOCATION_UPDATE));

        Intent detectedActivitiesIntent = new Intent(Constants.DETECTED_ACTIVITY_UPDATE);
        detectedActivitiesPI = PendingIntent.getBroadcast(this, 9002, detectedActivitiesIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        registerReceiver(detectedActivitiesReceiver, new IntentFilter(Constants.DETECTED_ACTIVITY_UPDATE));

        // Receivers for start/stop recording
        registerReceiver(startRecordingReceiver, new IntentFilter(Constants.START_RECORDING));
        registerReceiver(stopRecordingReceiver, new IntentFilter(Constants.STOP_RECORDING));
        registerReceiver(startAggressiveReceiver, new IntentFilter(Constants.CHANGE_AGGRESSIVE));

        // Location criteria
        criteria = new Criteria();
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(true);
        criteria.setCostAllowed(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        if (intent != null) {

            distanceFilter = Integer.parseInt(intent.getStringExtra("distanceFilter"));
            desiredAccuracy = Integer.parseInt(intent.getStringExtra("desiredAccuracy"));

            interval             = Integer.parseInt(intent.getStringExtra("interval"));
            fastestInterval      = Integer.parseInt(intent.getStringExtra("fastestInterval"));
            aggressiveInterval   = Integer.parseInt(intent.getStringExtra("aggressiveInterval"));
            activitiesInterval   = Integer.parseInt(intent.getStringExtra("activitiesInterval"));

            isDebugging = Boolean.parseBoolean(intent.getStringExtra("isDebugging"));
            notificationTitle = intent.getStringExtra("notificationTitle");
            notificationText = intent.getStringExtra("notificationText");

            useActivityDetection = Boolean.parseBoolean(intent.getStringExtra("useActivityDetection"));


            // Build the notification / pending intent
            Intent main = new Intent(this, BackgroundLocationServicesPlugin.class);
            main.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, main,  PendingIntent.FLAG_UPDATE_CURRENT);

            Context context = getApplicationContext();

            Notification.Builder builder = new Notification.Builder(this);
            builder.setContentTitle(notificationTitle);
            builder.setContentText(notificationText);
            builder.setSmallIcon(context.getApplicationInfo().icon);

            Bitmap bm = BitmapFactory.decodeResource(context.getResources(),
                                           context.getApplicationInfo().icon);

            float mult = getImageFactor(getResources());
            Bitmap scaledBm = Bitmap.createScaledBitmap(bm, (int)(bm.getWidth()*mult), (int)(bm.getHeight()*mult), false);

            if(scaledBm != null) {
              builder.setLargeIcon(scaledBm);
            }

            // Integer resId = getPluginResource("location_icon");
            //
            // //Scale our location_icon.png for different phone resolutions
            // //TODO: Get this icon via a filepath from the user
            // if(resId != 0) {
            //     Bitmap bm = BitmapFactory.decodeResource(getResources(), resId);
            //
            //     float mult = getImageFactor(getResources());
            //     Bitmap scaledBm = Bitmap.createScaledBitmap(bm, (int)(bm.getWidth()*mult), (int)(bm.getHeight()*mult), false);
            //
            //     if(scaledBm != null) {
            //         builder.setLargeIcon(scaledBm);
            //     }
            // } else {
            //     Log.w(TAG, "Could NOT find Resource for large icon");
            // }

            //Make clicking the event link back to the main cordova activity
            builder.setContentIntent(pendingIntent);
            setClickEvent(builder);

            Notification notification;
            if (android.os.Build.VERSION.SDK_INT >= 16) {
                notification = buildForegroundNotification(builder);
            } else {
                notification = buildForegroundNotificationCompat(builder);
            }

            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;
            startForeground(startId, notification);
        }

        // Log.i(TAG, "- url: " + url);
        // Log.i(TAG, "- params: "  + params.toString());
        Log.i(TAG, "- interval: "             + interval);
        Log.i(TAG, "- fastestInterval: "      + fastestInterval);

        Log.i(TAG, "- distanceFilter: "     + distanceFilter);
        Log.i(TAG, "- desiredAccuracy: "    + desiredAccuracy);
        Log.i(TAG, "- isDebugging: "        + isDebugging);
        Log.i(TAG, "- notificationTitle: "  + notificationTitle);
        Log.i(TAG, "- notificationText: "   + notificationText);
        Log.i(TAG, "- useActivityDetection: "   + useActivityDetection);
        Log.i(TAG, "- activityDetectionInterval: "   + activitiesInterval);

        //We want this service to continue running until it is explicitly stopped
        return START_REDELIVER_INTENT;
    }

    //Receivers for setting the plugin to a certain state
    private BroadcastReceiver startAggressiveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setStartAggressiveTrackingOn();
        }
    };

    private BroadcastReceiver startRecordingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(isDebugging) {
               Log.d(TAG, "- Start Recording Receiver");
            }

            if(useActivityDetection) {
              Log.d(TAG, "STARTING ACTIVITY DETECTION");
              startDetectingActivities();
            }

            startRecording();
        }
    };

    private BroadcastReceiver stopRecordingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(isDebugging) {
                Log.d(TAG, "- Stop Recording Receiver");
            }
            if(useActivityDetection) {
              stopDetectingActivities();
            }

            stopRecording();
        }
    };

    /**
     * Broadcast receiver for receiving a single-update from LocationManager.
     */
    private BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String key = FusedLocationProviderApi.KEY_LOCATION_CHANGED;
            Location location = (Location)intent.getExtras().get(key);

            if (location != null) {

                if(isDebugging) {
                    Log.d(TAG, "- locationUpdateReceiver" + location.toString());
                }

              // Go ahead and cache, push to server
              lastLocation = location;

              //This is all for setting the callback for android which currently does not work
               Intent mIntent = new Intent(Constants.CALLBACK_LOCATION_UPDATE);
               mIntent.putExtras(createLocationBundle(location));
               getApplicationContext().sendBroadcast(mIntent);

                // postLocation(location);
            }
        }
    };

    private void showDebugToast(Context ctx, String msg) {
        if(isDebugging) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private BroadcastReceiver detectedActivitiesReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        ArrayList<DetectedActivity> detectedActivities = (ArrayList) result.getProbableActivities();

        //Find the activity with the highest percentage
        lastActivity = Constants.getProbableActivity(detectedActivities);

        Log.w(TAG, "MOST LIKELY ACTIVITY: " + Constants.getActivityString(lastActivity.getType()) + " " + lastActivity.getConfidence());

        Intent mIntent = new Intent(Constants.CALLBACK_ACTIVITY_UPDATE);
        mIntent.putExtra(Constants.ACTIVITY_EXTRA, detectedActivities);
        getApplicationContext().sendBroadcast(mIntent);
        Log.w(TAG, "Activity is recording" + isRecording);

        if(lastActivity.getType() == DetectedActivity.STILL && isRecording) {
            showDebugToast(context, "Detected Activity was STILL, Stop recording");
            stopRecording();
        } else if(lastActivity.getType() != DetectedActivity.STILL && !isRecording) {
            showDebugToast(context, "Detected Activity was ACTIVE, Start Recording");
            startRecording();
        }
        //else do nothing
      }
    };

    //Helper function to get the screen scale for our big icon
    public float getImageFactor(Resources r) {
         DisplayMetrics metrics = r.getDisplayMetrics();
         float multiplier=metrics.density/3f;
         return multiplier;
    }

    //retrieves the plugin resource ID from our resources folder for a given drawable name
    public Integer getPluginResource(String resourceName) {
        return getApplication().getResources().getIdentifier(resourceName, "drawable", getApplication().getPackageName());
    }

    /**
     * Adds an onclick handler to the notification
     */
    private Notification.Builder setClickEvent (Notification.Builder notification) {
        Context context     = getApplicationContext();
        String packageName  = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestCode = new Random().nextInt();

        PendingIntent contentIntent = PendingIntent.getActivity(context, requestCode, launchIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        return notification.setContentIntent(contentIntent);
    }

    private Bundle createLocationBundle(Location location) {
      Bundle b = new Bundle();
      b.putDouble("latitude", location.getLatitude());
      b.putDouble("longitude", location.getLongitude());
      b.putDouble("accuracy", location.getAccuracy());
      b.putDouble("altitude", location.getAltitude());
      b.putDouble("timestamp", location.getTime());
      b.putDouble("speed", location.getSpeed());
      b.putDouble("heading", location.getBearing());

      return b;
    }

    private boolean enabled = false;
    private boolean startRecordingOnConnect = true;

    private void enable() {
        this.enabled = true;
    }

    private void disable() {
        this.enabled = false;
    }

    private void setStartAggressiveTrackingOn() {
        if(!fastestSpeed && this.isRecording) {
            detachRecorder();

            desiredAccuracy = 10;
            fastestInterval = (long) (aggressiveInterval / 2);
            interval = aggressiveInterval;

            attachRecorder();

            Log.e(TAG, "Changed Location params" + locationRequest.toString());
            fastestSpeed = true;
        }
    }

    public void startDetectingActivities() {
      this.isRequestingActivity = true;
      attachDARecorder();
    }

    public void stopDetectingActivities() {
      this.isRequestingActivity = false;
      detatchDARecorder();
    }

    private void attachDARecorder() {
      if (detectedActivitiesAPI == null) {
          buildDAClient();
      } else if (detectedActivitiesAPI.isConnected()) {
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                        detectedActivitiesAPI,
                        this.activitiesInterval,
                        detectedActivitiesPI
                );
          if(isDebugging) {
              Log.d(TAG, "- DA RECORDER attached - start recording location updates");
          }
      } else {
        Log.i(TAG, "NOT CONNECTED, CONNECT");
          detectedActivitiesAPI.connect();
      }
    }

    private void detatchDARecorder() {
      if (detectedActivitiesAPI == null) {
          buildDAClient();
      } else if (detectedActivitiesAPI.isConnected()) {
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(detectedActivitiesAPI, detectedActivitiesPI);
          if(isDebugging) {
              Log.d(TAG, "- Recorder detached - stop recording activity updates");
          }
      } else {
          detectedActivitiesAPI.connect();
      }
    }


    public void startRecording() {
        Log.w(TAG, "Started Recording Locations");
        this.startRecordingOnConnect = true;
        attachRecorder();
    }

    public void stopRecording() {
        this.startRecordingOnConnect = false;
        detachRecorder();
    }

    private GoogleApiClient.ConnectionCallbacks cb = new GoogleApiClient.ConnectionCallbacks() {
           @Override
           public void onConnected(Bundle bundle) {
               Log.w(TAG, "Activity Client Connected");
               ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                       detectedActivitiesAPI,
                       activitiesInterval,
                       detectedActivitiesPI
               );
           }
           @Override
           public void onConnectionSuspended(int i) {
               Log.w(TAG, "Connection To Activity Suspended");
               showDebugToast(getApplicationContext(), "Activity Client Suspended");
           }
       };

      private GoogleApiClient.OnConnectionFailedListener failedCb = new GoogleApiClient.OnConnectionFailedListener() {
           @Override
           public void onConnectionFailed(ConnectionResult cr) {
               Log.w(TAG, "ERROR CONNECTING TO DETECTED ACTIVITIES");
           }
       };

    protected synchronized void buildDAClient() {
      Log.i(TAG, "BUILDING DA CLIENT");
         detectedActivitiesAPI = new GoogleApiClient.Builder(this)
                 .addApi(ActivityRecognition.API)
                 .addConnectionCallbacks(cb)
                 .addOnConnectionFailedListener(failedCb)
                 .build();

        detectedActivitiesAPI.connect();
    }

    protected synchronized void connectToPlayAPI() {
        locationClientAPI =  new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        locationClientAPI.connect();
    }

    private void attachRecorder() {
      Log.i(TAG, "Attaching Recorder");
        if (locationClientAPI == null) {
            connectToPlayAPI();
        } else if (locationClientAPI.isConnected()) {
            locationRequest = LocationRequest.create()
                    .setPriority(translateDesiredAccuracy(desiredAccuracy))
                    .setFastestInterval(fastestInterval)
                    .setInterval(interval)
                    .setSmallestDisplacement(distanceFilter);
            LocationServices.FusedLocationApi.requestLocationUpdates(locationClientAPI, locationRequest, locationUpdatePI);
            this.isRecording = true;

            if(isDebugging) {
                Log.d(TAG, "- Recorder attached - start recording location updates");
            }
        } else {
            locationClientAPI.connect();
        }
    }

    private void detachRecorder() {
        if (locationClientAPI == null) {
            connectToPlayAPI();
        } else if (locationClientAPI.isConnected()) {
            //flush the location updates from the api
            LocationServices.FusedLocationApi.removeLocationUpdates(locationClientAPI, locationUpdatePI);
            this.isRecording = false;
            if(isDebugging) {
                Log.w(TAG, "- Recorder detached - stop recording location updates");
            }
        } else {
            locationClientAPI.connect();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "- Connected to Play API -- All ready to record");
        if (this.startRecordingOnConnect) {
            attachRecorder();
        } else {
            detachRecorder();
        }
    }

    @Override
    public void onConnectionFailed(com.google.android.gms.common.ConnectionResult result) {
        Log.e(TAG, "We failed to connect to the Google API! Possibly API is not installed on target.");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // locationClientAPI.connect();
    }

    @TargetApi(16)
    private Notification buildForegroundNotification(Notification.Builder builder) {
        return builder.build();
    }

    @SuppressWarnings("deprecation")
    @TargetApi(15)
    private Notification buildForegroundNotificationCompat(Notification.Builder builder) {
        return builder.getNotification();
    }

    /**
     * Translates a number representing desired accuracy of GeoLocation system from set [0, 10, 100, 1000].
     * 0:  most aggressive, most accurate, worst battery drain
     * 1000:  least aggressive, least accurate, best for battery.
     */
    private Integer translateDesiredAccuracy(Integer accuracy) {
        if(accuracy <= 0) {
            accuracy = LocationRequest.PRIORITY_HIGH_ACCURACY;
        } else if(accuracy <= 100) {
            accuracy = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
        } else if(accuracy  <= 1000) {
            accuracy = LocationRequest.PRIORITY_LOW_POWER;
        } else if(accuracy <= 10000) {
            accuracy = LocationRequest.PRIORITY_NO_POWER;
        } else {
          accuracy = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
        }

        return accuracy;
    }

    private boolean isNetworkConnected() {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            Log.d(TAG, "Network found, type = " + networkInfo.getTypeName());
            return networkInfo.isConnected();
        } else {
            Log.d(TAG, "No active network info");
            return false;
        }
    }

    @Override
    public boolean stopService(Intent intent) {
        Log.i(TAG, "- Received stop: " + intent);
        this.stopRecording();
        this.cleanUp();

        showDebugToast(this, "Background location tracking stopped");
        return super.stopService(intent);
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "Destroyed Location Update Service - Cleaning up");
        this.cleanUp();
        super.onDestroy();
    }

    private void cleanUp() {
        try {
            unregisterReceiver(locationUpdateReceiver);
            unregisterReceiver(startRecordingReceiver);
            unregisterReceiver(stopRecordingReceiver);
            unregisterReceiver(detectedActivitiesReceiver);
        } catch(IllegalArgumentException e) {
               Log.e(TAG, "Error: Could not unregister receiver", e);
        }

        try {
            stopForeground(true);
        } catch (Exception e) {
            Log.e(TAG, "Error: Could not stop foreground process", e);
        }


        toneGenerator.release();
        if(locationClientAPI != null) {
            locationClientAPI.disconnect();
        }

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        this.stopRecording();
        this.stopSelf();
        super.onTaskRemoved(rootIntent);
    }

}
