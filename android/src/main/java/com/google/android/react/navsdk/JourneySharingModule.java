package com.google.android.react.navsdk;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.mapsplatform.transportation.consumer.auth.AuthTokenContext;
import com.google.android.libraries.mapsplatform.transportation.consumer.auth.AuthTokenFactory;
import com.google.android.libraries.mapsplatform.transportation.consumer.ConsumerApi;
import com.google.android.libraries.mapsplatform.transportation.consumer.managers.TripModel;
import com.google.android.libraries.mapsplatform.transportation.consumer.managers.TripModelManager;
import com.google.android.libraries.mapsplatform.transportation.consumer.sessions.JourneySharingSession;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerController;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerGoogleMap;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@ReactModule(name = JourneySharingModule.NAME)
public class JourneySharingModule extends ReactContextBaseJavaModule {
  public static final String NAME = "JourneySharingModule";
  private static final String TAG = "JourneySharingModule";
  private static final float DEFAULT_ZOOM = 15.0f;

  private final ReactApplicationContext reactContext;
  private String providerId;
  private String providerToken;
  private String tripId;
  private boolean isSessionActive = false;

  // Fleet Engine SDK components
  private ConsumerApi consumerApi;
  private TripModelManager tripModelManager;
  private final Executor executor = Executors.newSingleThreadExecutor();

  // Journey sharing components
  private ConsumerGoogleMap consumerGoogleMap;
  private ConsumerController consumerController;
  private JourneySharingSession journeySharingSession;
  private FrameLayout mapContainer;

  public JourneySharingModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  /**
   * Initialize the journey sharing module with provider credentials
   */
  @ReactMethod
  public void initialize(String providerId, @Nullable String providerToken, final Promise promise) {
    Log.d(TAG, "Initializing JourneySharing with providerId: " + providerId);

    this.providerId = providerId;
    this.providerToken = providerToken;

    try {
      // Initialize ConsumerApi with provider ID and token
      ConsumerApi.initialize(
        getCurrentActivity(),
        providerId,
        new AuthTokenFactory() {
          @Override
          public String getToken(AuthTokenContext context) {
            return providerToken;
          }
        }
      ).addOnSuccessListener(api -> {
        consumerApi = api;
        tripModelManager = consumerApi.getTripModelManager();
        Log.d(TAG, "Fleet Engine Consumer API initialized successfully");
        promise.resolve(true);
      }).addOnFailureListener(e -> {
        Log.e(TAG, "Error initializing Fleet Engine Consumer API", e);
        promise.reject("INIT_ERROR", "Failed to initialize Fleet Engine: " + e.getMessage());
      });
    } catch (Exception e) {
      Log.e(TAG, "Error initializing Fleet Engine Consumer API", e);
      promise.reject("INIT_ERROR", "Failed to initialize Fleet Engine: " + e.getMessage());
    }
  }

  /**
   * Update the provider token
   */
  @ReactMethod
  public void updateProviderToken(String token, final Promise promise) {
    Log.d(TAG, "Updating provider token");
    this.providerToken = token;
    promise.resolve(true);
  }

  /**
   * Set up the map view in the provided container ID
   */
  @ReactMethod
  public void setupMapView(final int containerId, final Promise promise) {
    final Activity activity = getCurrentActivity();
    if (activity == null) {
      promise.reject("ACTIVITY_ERROR", "Current activity is null");
      return;
    }

    activity.runOnUiThread(() -> {
      try {
        // Find the container view by ID
        View rootView = activity.findViewById(android.R.id.content);
        View containerView = findViewByReactId(rootView, containerId);

        if (containerView instanceof ViewGroup) {
          mapContainer = new FrameLayout(activity);
          ((ViewGroup) containerView).addView(mapContainer);

          // Initialize the map
          initializeConsumerMap(promise);
        } else {
          promise.reject("CONTAINER_ERROR", "Container view not found or not a ViewGroup");
        }
      } catch (Exception e) {
        Log.e(TAG, "Error setting up map view", e);
        promise.reject("SETUP_ERROR", "Failed to set up map view: " + e.getMessage());
      }
    });
  }

  private void initializeConsumerMap(final Promise promise) {
    if (mapContainer == null) {
      promise.reject("CONTAINER_ERROR", "Map container is null");
      return;
    }

    // Use the correct method signature based on the SDK version
    ConsumerGoogleMap.create(
      getCurrentActivity(),
      null,
      mapContainer,
      map -> {
        consumerGoogleMap = map;
        consumerController = map.getConsumerController();
        Log.d(TAG, "Consumer map ready");
        promise.resolve(true);
      }
    );
  }

  /**
   * Start journey sharing session with trip data
   */
  @ReactMethod
  public void startJourneySharing(ReadableMap tripDataMap, final Promise promise) {
    Log.d(TAG, "Starting journey sharing");

    if (isSessionActive) {
      promise.reject("SESSION_ACTIVE", "Journey sharing session is already active");
      return;
    }

    if (consumerApi == null || tripModelManager == null || consumerController == null) {
      promise.reject("NOT_INITIALIZED", "Fleet Engine Consumer API or map not initialized");
      return;
    }

    try {
      // Extract trip data from JS object
      String tripName = tripDataMap.getString("tripName");
      int tripStatus = tripDataMap.getInt("tripStatus");
      this.tripId = tripName;

      // Get or create trip model
      TripModel tripModel = tripModelManager.getTripModel(tripName);

      // Create and start journey sharing session
      journeySharingSession = JourneySharingSession.createInstance(tripModel);
      if (journeySharingSession != null) {
        consumerController.showSession(journeySharingSession);
        isSessionActive = true;

        // Send status update event to React Native
        WritableMap params = Arguments.createMap();
        params.putString("tripId", tripName);
        params.putBoolean("isActive", true);
        sendEvent("journeySharingStatusChanged", params);

        promise.resolve(true);
      } else {
        promise.reject("SESSION_ERROR", "Failed to create journey sharing session");
      }
    } catch (Exception e) {
      Log.e(TAG, "Error starting journey sharing", e);
      promise.reject("START_ERROR", "Failed to start journey sharing: " + e.getMessage());
    }
  }

  /**
   * Stop journey sharing session
   */
  @ReactMethod
  public void stopJourneySharing(final Promise promise) {
    Log.d(TAG, "Stopping journey sharing");

    if (!isSessionActive) {
      promise.reject("NO_SESSION", "No active journey sharing session");
      return;
    }

    try {
      if (journeySharingSession != null) {
        journeySharingSession.stop();
        journeySharingSession = null;
      }

      if (consumerController != null) {
        consumerController.hideAllSessions();
      }

      isSessionActive = false;

      // Send status update event to React Native
      WritableMap params = Arguments.createMap();
      params.putString("tripId", tripId);
      params.putBoolean("isActive", false);
      sendEvent("journeySharingStatusChanged", params);

      promise.resolve(true);
    } catch (Exception e) {
      Log.e(TAG, "Error stopping journey sharing", e);
      promise.reject("STOP_ERROR", "Failed to stop journey sharing: " + e.getMessage());
    }
  }

  /**
   * Update current location for the journey sharing map
   */
  @ReactMethod
  public void updateCurrentLocation(ReadableMap locationMap, final Promise promise) {
    try {
      if (consumerGoogleMap != null) {
        double latitude = locationMap.getDouble("latitude");
        double longitude = locationMap.getDouble("longitude");

        LatLng latLng = new LatLng(latitude, longitude);
        consumerGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));
        promise.resolve(true);
      } else {
        promise.reject("MAP_ERROR", "Consumer map is not initialized");
      }
    } catch (Exception e) {
      Log.e(TAG, "Error updating location", e);
      promise.reject("LOCATION_ERROR", "Failed to update location: " + e.getMessage());
    }
  }

  /**
   * Find out if the journey sharing is currently active
   */
  @ReactMethod
  public void isJourneySharingActive(final Promise promise) {
    promise.resolve(isSessionActive);
  }

  /**
   * Send an event to React Native
   */
  private void sendEvent(String eventName, @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  /**
   * Helper method to find a view by its React ID
   */
  private View findViewByReactId(View view, int reactId) {
    if (view.getId() == reactId) {
      return view;
    }

    if (view instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) view;
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        View child = findViewByReactId(viewGroup.getChildAt(i), reactId);
        if (child != null) {
          return child;
        }
      }
    }

    return null;
  }

  /**
   * Clean up resources when the module is destroyed
   */
  @Override
  public void invalidate() {
    if (isSessionActive) {
      try {
        if (journeySharingSession != null) {
          journeySharingSession.stop();
          journeySharingSession = null;
        }

        if (consumerController != null) {
          consumerController.hideAllSessions();
        }

        isSessionActive = false;
      } catch (Exception e) {
        Log.e(TAG, "Error cleaning up journey sharing", e);
      }
    }
    super.invalidate();
  }
}
