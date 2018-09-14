package com.tiagojdferreira;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;


import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableNativeMap;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationListener;

import com.google.android.gms.common.GooglePlayServicesUtil;

/**
 * Created by corentin on 10/29/15.
 */
public class RNGeolocationModule extends ReactContextBaseJavaModule implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    public abstract interface LocationCallback {
        public abstract void handleNewLocation(Location location);
    }
    // Location Callback for later use
    private LocationCallback mLocationCallback;
    // Unique Name for Log TAG
    public static final String TAG = RNGeolocationModule.class.getSimpleName();
    // Are we Connected?
    public Boolean connected;

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    // Context for later use
    private Context mContext;
    // Main Google API CLient (Google Play Services API)
    private GoogleApiClient mGoogleApiClient;
    // Location Request for later use
    private LocationRequest mLocationRequest;

    public RNGeolocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "RNGeolocationManager";
    }

    /**
     * @param timeout
     * @param maximumAge
     * @param enableHighAccuracy true -> PRIORITY_HIGH_ACCURACY
     *                           false -> PRIORITY_BALANCED_POWER_ACCURACY
     * @param onSuccess
     * @param onError
     */
    @SuppressWarnings("unused")
    @ReactMethod
    public void getCurrentPosition(final Integer timeout,
                                   final Integer maximumAge,
                                   final Boolean enableHighAccuracy,
                                   final Callback onSuccess,
                                   final Callback onError) {
        if (onSuccess == null) {
            Log.e(getName(), "no success callback");
            return;
        }
        if (onError == null) {
            Log.e(getName(), "no error callback");
            return;
        }

        if (mGoogleApiClient == null) {
            Log.d(getName(), "no mGoogleApiClient");
            mGoogleApiClient = new GoogleApiClient.Builder(getReactApplicationContext())
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle connectionHint) {
                            Log.d(getName(), "onConnected");
                            _getLastKnownLocation(onSuccess, onError, timeout,
                                    maximumAge, enableHighAccuracy);
                        }

                        @Override
                        public void onConnectionSuspended(int cause) {
                            Log.e(getName(), "onConnectionSuspended: " + cause);
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult result) {
                            Log.e(getName(), "onConnectionFailed");
                            onError.invoke();
                            mGoogleApiClient.disconnect();
                        }
                    })
                    .addApi(LocationServices.API)
                    .build();
            
            mLocationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                    .setFastestInterval(1000);     // 1 second, in milliseconds

            mGoogleApiClient.connect();
        }
        else if (mGoogleApiClient.isConnected()) {
            Log.d(getName(), "mGoogleApiClient is connected");
            _getLastKnownLocation(onSuccess, onError, timeout, maximumAge, enableHighAccuracy);
        }
        else {
            Log.d(getName(), "mGoogleApiClient is connecting");
            mGoogleApiClient.connect();
        }
    }

    private void _getLastKnownLocation(final Callback onSuccess,
                                       final Callback onError,
                                       Integer timeout,
                                       Integer maximumAge,
                                       Boolean enableHighAccuracy) {

        Log.d(getName(), "tiago: calling _getLastKnownLocation");
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        Log.e(getName(), "lastLocation: " + lastLocation);

        if (lastLocation == null) {
            Log.e(getName(), "lastLocation null");
            onError.invoke();
            return;
        }

        Log.e(getName(), "lastLocation found: " + lastLocation.toString());

        WritableNativeMap result = new WritableNativeMap();
        WritableNativeMap coords = new WritableNativeMap();

        coords.putDouble("latitude", lastLocation.getLatitude());
        coords.putDouble("longitude", lastLocation.getLongitude());
        result.putMap("coords", coords);

        onSuccess.invoke(result);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }
    @Override
    public void onLocationChanged(Location location) {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }
    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Location services connected.");
        // We are Connected!
        connected = true;
        // First, get Last Location and return it to Callback
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location != null) {
            mLocationCallback.handleNewLocation(location);
        }
        // Now request continuous Location Updates
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution() && mContext instanceof Activity) {
            try {
                Activity activity = (Activity)mContext;
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(activity, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            /*
             * Thrown if Google Play services canceled the original
             * PendingIntent
             */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }
    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services suspended...");
    }
}
