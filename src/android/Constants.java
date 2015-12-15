package com.flybuy.cordova.location;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import android.util.Log;
import java.util.ArrayList;
import android.content.res.Resources;

public class Constants {
  private Constants() {}

  private static final String P_NAME = "com.flybuy.cordova.location.";

  //Receiver Paths for both
  public static final String STOP_RECORDING  = P_NAME + "STOP_RECORDING";
  public static final String START_RECORDING = P_NAME + "START_RECORDING";
  public static final String CHANGE_AGGRESSIVE = P_NAME + "CHANGE_AGGRESSIVE";
  public static final String STOP_GEOFENCES = P_NAME + "STOP_GEOFENCES";
  public static final String CALLBACK_LOCATION_UPDATE = P_NAME + "CALLBACK_LOCATION_UPDATE";
  public static final String CALLBACK_ACTIVITY_UPDATE = P_NAME + "CALLBACK_ACTIVITY_UPDATE";
  public static final String ACTIVITY_EXTRA = P_NAME + ".ACTIVITY_EXTRA";

  //Receiver paths for service
  public static String LOCATION_UPDATE = P_NAME + "LOCATION_UPDATE";
  public static String DETECTED_ACTIVITY_UPDATE = P_NAME + "DETECTED_ACTIVITY_UPDATE";

  private static final String ConstantsTAG = "Constants";


  public static String getActivityString(int detectedActivityType) {
        switch(detectedActivityType) {
            case DetectedActivity.IN_VEHICLE:
                return "IN_VEHICLE";
            case DetectedActivity.ON_BICYCLE:
                return "ON_BICYCLE";
            case DetectedActivity.ON_FOOT:
                return "ON_FOOT";
            case DetectedActivity.RUNNING:
                return "RUNNING";
            case DetectedActivity.STILL:
                return "STILL";
            case DetectedActivity.TILTING:
                return "TILTING";
            case DetectedActivity.UNKNOWN:
                return "UNKNOWN";
            case DetectedActivity.WALKING:
                return "WALKING";
            default:
                return "Unknown";
        }
    }

    public static DetectedActivity getProbableActivity(ArrayList<DetectedActivity> detectedActivities) {
      int highestConfidence = 0;
      DetectedActivity mostLikelyActivity = new DetectedActivity(0, DetectedActivity.UNKNOWN);

      for(DetectedActivity da: detectedActivities) {
        if(da.getType() != DetectedActivity.TILTING || da.getType() != DetectedActivity.UNKNOWN) {
          Log.w(ConstantsTAG, "Received a Detected Activity that was not tilting / unknown");
          if (highestConfidence < da.getConfidence()) {
            highestConfidence = da.getConfidence();
            mostLikelyActivity = da;

          }
        }
      }
      return mostLikelyActivity;
    }
}
