package com.google.android.react.navsdk;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.bridge.UiThreadUtil;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerController;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerGoogleMap;

import java.lang.reflect.Method;
import java.util.Map;

public class ConsumerMapViewManager extends ViewGroupManager<FrameLayout> {
  private static final String REACT_CLASS = "ConsumerMapView";
  private final ReactApplicationContext reactContext;
  private FrameLayout mapContainer;
  private ConsumerGoogleMap consumerGoogleMap;
  private ConsumerController consumerController;

  public ConsumerMapViewManager(ReactApplicationContext reactContext) {
    this.reactContext = reactContext;
  }

  @NonNull
  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @NonNull
  @Override
  protected FrameLayout createViewInstance(@NonNull ThemedReactContext reactContext) {
    mapContainer = new FrameLayout(reactContext);
    return mapContainer;
  }

  @ReactMethod
  public void initialize(final Promise promise) {
    UiThreadUtil.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (mapContainer == null) {
          promise.reject("CONTAINER_ERROR", "Map container is null");
          return;
        }

        Activity activity = reactContext.getCurrentActivity();
        if (activity == null) {
          promise.reject("ACTIVITY_ERROR", "Activity is null");
          return;
        }

        try {
          // Find the MapViewConsumer component in your app
          // This assumes you have a MapViewConsumer in your application
          // Replace "com.customer.fivecanale.rider.map.MapViewConsumer" with your actual class path
          Class<?> mapViewConsumerClass = Class.forName("com.customer.fivecanale.rider.map.MapViewConsumer");

          // Create an instance of MapViewConsumer and add it to the container
          View mapViewConsumer = (View) mapViewConsumerClass.getConstructor(Context.class).newInstance(activity);
          mapContainer.addView(mapViewConsumer, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

          // Call getConsumerGoogleMapAsync on the mapViewConsumer
          Method getConsumerGoogleMapAsyncMethod = mapViewConsumerClass.getMethod(
            "getConsumerGoogleMapAsync",
            ConsumerGoogleMap.ConsumerMapReadyCallback.class,
            FragmentActivity.class,
            GoogleMapOptions.class);

          getConsumerGoogleMapAsyncMethod.invoke(mapViewConsumer,
            new ConsumerGoogleMap.ConsumerMapReadyCallback() {
              @Override
              public void onConsumerMapReady(@NonNull ConsumerGoogleMap map) {
                consumerGoogleMap = map;
                consumerController = map.getConsumerController();

                // Write map ready event to JS
                WritableMap event = Arguments.createMap();
                event.putBoolean("isReady", true);
                sendEvent("onMapReady", event);

                promise.resolve(true);
              }
            },
            activity,
            null);

        } catch (Exception e) {
          promise.reject("INIT_ERROR", "Failed to initialize map: " + e.getMessage(), e);
        }
      }
    });
  }

  private void sendEvent(String eventName, WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  @ReactMethod
  public void moveCamera(ReadableMap position, Promise promise) {
    UiThreadUtil.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          if (consumerGoogleMap == null) {
            promise.reject("MAP_NOT_READY", "Map is not ready yet");
            return;
          }

          double lat = position.getDouble("latitude");
          double lng = position.getDouble("longitude");
          float zoom = (float) position.getDouble("zoom");

          CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
            new LatLng(lat, lng), zoom);
          consumerGoogleMap.moveCamera(cameraUpdate);
          promise.resolve(true);
        } catch (Exception e) {
          promise.reject("CAMERA_ERROR", e.getMessage(), e);
        }
      }
    });
  }

  @ReactMethod
  public void addMarker(ReadableMap markerOptions, Promise promise) {
    UiThreadUtil.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          if (consumerGoogleMap == null) {
            promise.reject("MAP_NOT_READY", "Map is not ready yet");
            return;
          }

          double lat = markerOptions.getDouble("latitude");
          double lng = markerOptions.getDouble("longitude");
          String title = markerOptions.hasKey("title") ? markerOptions.getString("title") : "";

          MarkerOptions options = new MarkerOptions()
            .position(new LatLng(lat, lng))
            .title(title);

          Marker marker = consumerGoogleMap.addMarker(options);
          promise.resolve(true);
        } catch (Exception e) {
          promise.reject("MARKER_ERROR", e.getMessage(), e);
        }
      }
    });
  }

  @Override
  public void onDropViewInstance(FrameLayout view) {
    super.onDropViewInstance(view);
    // Clean up resources
    consumerGoogleMap = null;
    consumerController = null;
    mapContainer = null;
  }

  // Get the consumer controller for journey sharing
  public ConsumerController getConsumerController() {
    return consumerController;
  }
}
