package com.flybuy.cordova.location;

import java.util.List;
import java.util.Iterator;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
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

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.content.res.Resources;

//Detected Activities imports

public class BackgroundLocationUpdateService
        extends Service
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "BackgroundLocationUpdateService";

    private Location lastLocation;
    private long lastUpdateTime = 0l;
    private Boolean fastestSpeed = false;

    private PendingIntent locationUpdatePI;
    private GoogleApiClient locationClientAPI;

    private Integer desiredAccuracy = 100;
    private Integer distanceFilter  = 30;

    private static final Integer SECONDS_PER_MINUTE      = 60;
    private static final Integer MILLISECONDS_PER_SECOND = 60;

    private long  interval             = (long)  SECONDS_PER_MINUTE * MILLISECONDS_PER_SECOND * 5;
    private long  fastestInterval      = (long)  SECONDS_PER_MINUTE * MILLISECONDS_PER_SECOND;
    private long  aggressiveInterval   = (long) MILLISECONDS_PER_SECOND * 4;

    private Boolean isDebugging;
    private String notificationTitle = "Background checking";
    private String notificationText = "ENABLED";
    private Boolean stopOnTerminate;

    private ToneGenerator toneGenerator;

    private Criteria criteria;

    private ConnectivityManager connectivityManager;
    private NotificationManager notificationManager;

    private LocationRequest locationRequest;

    private JSONObject params;
    private String url = "localhost:3000/location";
    private JSONObject headers;


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

            try {
                params = new JSONObject(intent.getStringExtra("params"));
                headers = new JSONObject(intent.getStringExtra("headers"));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            //Get our POST url and configure options from the intent
            url = intent.getStringExtra("url");

            distanceFilter = Integer.parseInt(intent.getStringExtra("distanceFilter"));
            desiredAccuracy = Integer.parseInt(intent.getStringExtra("desiredAccuracy"));

            interval             = Integer.parseInt(intent.getStringExtra("interval"));
            fastestInterval      = Integer.parseInt(intent.getStringExtra("fastestInterval"));
            aggressiveInterval   = Integer.parseInt(intent.getStringExtra("aggressiveInterval"));

            isDebugging = Boolean.parseBoolean(intent.getStringExtra("isDebugging"));
            notificationTitle = intent.getStringExtra("notificationTitle");
            notificationText = intent.getStringExtra("notificationText");

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

        Log.i(TAG, "- url: " + url);
        Log.i(TAG, "- params: "  + params.toString());
        Log.i(TAG, "- interval: "             + interval);
        Log.i(TAG, "- fastestInterval: "      + fastestInterval);

        Log.i(TAG, "- distanceFilter: "     + distanceFilter);
        Log.i(TAG, "- desiredAccuracy: "    + desiredAccuracy);
        Log.i(TAG, "- isDebugging: "        + isDebugging);
        Log.i(TAG, "- notificationTitle: "  + notificationTitle);
        Log.i(TAG, "- notificationText: "   + notificationText);

        //We want this service to continue running until it is explicitly stopped
        return START_REDELIVER_INTENT;
    }

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
                    // Toast.makeText(context, "We recieveived a location update", Toast.LENGTH_SHORT).show();
                    // startTone("doodly_doo");
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

    private void postLocation(Location location) {

        PostLocationTask task = new BackgroundLocationUpdateService.PostLocationTask();
        Log.d(TAG, "Before post : Start Executor " +  task.getStatus());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, location);
        }
        else {
            task.execute(location);
        }

        Log.d(TAG, "After Post" + task.getStatus());

    }

    private class PostLocationTask extends AsyncTask<Object, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(Object... objects) {
            Log.d(TAG, "Executing PostLocationTask#doInBackground");
            return postLocationSync((Location)objects[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.d(TAG, "PostLocationTask#onPostExecture");
        }
    }

    private boolean postLocationSync(Location l) {
        if (l == null) {
            Log.w(TAG, "postLocation: null location");
            return false;
        }
        try {
            lastUpdateTime = SystemClock.elapsedRealtime();
            Log.i(TAG, "Posting  native location update: " + l);

            //Gets a http OR https tolerant client, supports both modes
            DefaultHttpClient httpClient = getTolerantClient();

            HttpPost request = new HttpPost(url);

            //Shove our location data into a JSON obhject
            JSONObject location = new JSONObject();
            location.put("latitude", l.getLatitude());
            location.put("longitude", l.getLongitude());
            location.put("accuracy", l.getAccuracy());
            location.put("speed", l.getSpeed());
            location.put("bearing", l.getBearing());
            location.put("altitude", l.getAltitude());

            params.put("location", location);

            Log.i(TAG, "Location To Be Posted: " + location.toString());

            //Create a string entity to for our param keys
            StringEntity se = new StringEntity(params.toString());
            request.setEntity(se);
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-type", "application/json");

            //Loop over our header keys and add them to our request
            Iterator<String> headkeys = headers.keys();
            while (headkeys.hasNext()) {
                String headkey = headkeys.next();
                if (headkey != null) {
                    Log.d(TAG, "Adding Header: " + headkey + " : " + (String) headers.getString(headkey));
                    request.setHeader(headkey, (String) headers.getString(headkey));
                }
            }

            Log.d(TAG, "Posting to " + request.getURI().toString());
            HttpResponse response = httpClient.execute(request);
            Log.i(TAG, "Response received: " + response.getStatusLine());

            //Get the status code that the http request sends back if there is any
            //This tells our plugin what to do in certain cases
            int res = response.getStatusLine().getStatusCode();

            //Users may fire a request code back to the plugin to initiate certain behavior:
            //Codes:
            //200 : Does nothing, simply there to mark that it received a pong
            //201 : Sets the location update receiver to aggresive mode -- useful for pin pointing a user for a short period of time
            //401 : Sets the location update receiver to kill mode -- useful for killing the clients location updates when their client cant (main app dead)
            switch (res) {
                case 200:
                    return true;
                case 201:
                    Log.w(TAG, "Plugin received a request to initiate aggresive mode");
                    if (!fastestSpeed) {
                        detachRecorder();
                        desiredAccuracy = 10;
                        fastestInterval = 500;
                        interval = 1000;
                        attachRecorder();

                        Log.e(TAG, "Changed Location params" + locationRequest.toString());
                        fastestSpeed = true;
                    }
                    return true;
                case 410:
                    Log.e(TAG, "ALERT --- : Got kill signal from server -- stopping location updates and killing update services");
                    this.stopRecording();
                    this.cleanUp();
                    return false;
                default:
                    return false;
            }

        } catch (Throwable e) {
            Log.w(TAG, "Exception posting location: " + e);
            e.printStackTrace();
            return false;
        }
    }

    //Retrieves the url string, and does the extra work required to make an https request if it detects https.
    //Returns an DefaultHttpClient
    public DefaultHttpClient getTolerantClient() {
        DefaultHttpClient client = new DefaultHttpClient();

        if(!(url.substring(0, 5)).equals("https")) {
            return client;
        }

        HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

        SchemeRegistry registry = new SchemeRegistry();
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
        registry.register(new Scheme("https", socketFactory, 443));
        SingleClientConnManager mgr = new SingleClientConnManager(client.getParams(), registry);
        DefaultHttpClient httpClient = new DefaultHttpClient(mgr, client.getParams());

        // Set verifier
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);

        return httpClient;
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
            startRecording();
        }
    };

    private BroadcastReceiver stopRecordingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(isDebugging) {
                Log.d(TAG, "- Stop Recording Receiver");
            }
            stopRecording();
        }
    };

    private boolean running = false;
    private boolean enabled = false;
    private boolean startRecordingOnConnect = true;

    private void enable() {
        this.enabled = true;
    }

    private void disable() {
        this.enabled = false;
    }

    private void setStartAggressiveTrackingOn() {
        if(!fastestSpeed && this.running) {
            detachRecorder();

            desiredAccuracy = 10;
            fastestInterval = (long) (aggressiveInterval / 2);
            interval = aggressiveInterval;

            attachRecorder();

            Log.e(TAG, "Changed Location params" + locationRequest.toString());
            fastestSpeed = true;
        }
    }

    public void startRecording() {
        this.startRecordingOnConnect = true;
        attachRecorder();
    }

    public void stopRecording() {
        this.startRecordingOnConnect = false;
        detachRecorder();
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
        if (locationClientAPI == null) {
            connectToPlayAPI();
        } else if (locationClientAPI.isConnected()) {
            locationRequest = LocationRequest.create()
                    .setPriority(translateDesiredAccuracy(desiredAccuracy))
                    .setFastestInterval(fastestInterval)
                    .setInterval(interval)
                    .setSmallestDisplacement(distanceFilter);
            LocationServices.FusedLocationApi.requestLocationUpdates(locationClientAPI, locationRequest, locationUpdatePI);
            this.running = true;
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
            this.running = false;
            if(isDebugging) {
                Log.d(TAG, "- Recorder detached - stop recording location updates");
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

    /**
     * Plays debug sound
     * @param name
     */
    private void startTone(String name) {
        int tone = 0;
        int duration = 1000;

        if (name.equals("beep")) {
            tone = ToneGenerator.TONE_PROP_BEEP;
        } else if (name.equals("beep_beep_beep")) {
            tone = ToneGenerator.TONE_CDMA_CONFIRM;
        } else if (name.equals("long_beep")) {
            tone = ToneGenerator.TONE_CDMA_ABBR_ALERT;
        } else if (name.equals("doodly_doo")) {
            tone = ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE;
        } else if (name.equals("chirp_chirp_chirp")) {
            tone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
        } else if (name.equals("dialtone")) {
            tone = ToneGenerator.TONE_SUP_RINGTONE;
        }
        toneGenerator.startTone(tone, duration);
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

        if (isDebugging) {
            Toast.makeText(this, "Background location tracking stopped", Toast.LENGTH_SHORT).show();
        }
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
