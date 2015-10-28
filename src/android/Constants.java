package com.flybuy.cordova.location;

public class Constants {
  private Constants() {}

  private static final String P_NAME = "com.flybuy.cordova.location.";

  //Receiver Paths for both
  public static final String STOP_RECORDING  = P_NAME + "STOP_RECORDING";
  public static final String START_RECORDING = P_NAME + "START_RECORDING";
  public static final String CHANGE_AGGRESSIVE = P_NAME + "CHANGE_AGGRESSIVE";
  public static final String STOP_GEOFENCES = P_NAME + "STOP_GEOFENCES";
  public static final String CALLBACK_LOCATION_UPDATE = P_NAME + "CALLBACK_LOCATION_UPDATE";

  //Receiver paths for service
  public static final String LOCATION_UPDATE = P_NAME + "LOCATION_UPDATE";
  public static final String DETECTED_ACTIVITY_UPDATE = P_NAME + "DETECTED_ACTIVITY_UPDATE";


}
