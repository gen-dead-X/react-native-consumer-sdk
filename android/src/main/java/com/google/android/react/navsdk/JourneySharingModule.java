/**
 * Copyright 2025 Google LLC
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.react.navsdk;

import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.CatalystInstance;
import com.facebook.react.bridge.NativeArray;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.module.annotations.ReactModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JourneySharingModule provides React Native interface to journey sharing functionality
 * for use in consumer applications. This allows tracking trips with Fleet Engine.
 * Note: This is a basic implementation with simulated/mock data for demonstration purposes.
 */
@ReactModule(name = JourneySharingModule.NAME)
public class JourneySharingModule extends ReactContextBaseJavaModule {
  public static final String NAME = "JourneySharingModule";
  private static final String TAG = "JourneySharingModule";

  private final ReactApplicationContext reactContext;
  private String providerId;
  private String providerToken;
  private String tripId;
  private boolean isSessionActive = false;

  // For development purposes, we'll use a simulated trip
  private SimulatedTrip simulatedTrip;

  // Core journey sharing data structures
  private static class LatLng {
    public double lat;
    public double lng;

    public LatLng(double lat, double lng) {
      this.lat = lat;
      this.lng = lng;
    }
  }

  public static class TripWaypoint {
    public String id;
    public LatLng location;
    public int waypointType; // 0=pickup, 1=dropoff, 2=intermediate
    public String tripId;
    public String title;

    public TripWaypoint(LatLng location, int waypointType, String tripId, String title) {
      this.id = UUID.randomUUID().toString();
      this.location = location;
      this.waypointType = waypointType;
      this.tripId = tripId;
      this.title = title;
    }
  }

  public static class TripData {
    public String tripName;
    public int tripStatus; // 0=NEW, 1=ENROUTE_TO_PICKUP, 2=ARRIVED_AT_PICKUP, 3=ENROUTE_TO_DROPOFF, 4=COMPLETED, 5=CANCELED
    public List<TripWaypoint> remainingWaypoints;
    public String vehicleTypeId;
    public String bookingId;

    public TripData(String tripName, int tripStatus, List<TripWaypoint> remainingWaypoints,
                   String vehicleTypeId, String bookingId) {
      this.tripName = tripName;
      this.tripStatus = tripStatus;
      this.remainingWaypoints = remainingWaypoints;
      this.vehicleTypeId = vehicleTypeId;
      this.bookingId = bookingId;
    }
  }

  private static class VehicleLocation {
    public LatLng location;
    public double heading;
    public long timestamp;

    public VehicleLocation(LatLng location, double heading, long timestamp) {
      this.location = location;
      this.heading = heading;
      this.timestamp = timestamp;
    }
  }

  private static class SimulatedTrip {
    public TripData tripData;
    public VehicleLocation vehicleLocation;
    public int currentStatus;
    public int currentWaypointIndex = 0;
    public long etaTimestampMillis;
    public double remainingDistanceMeters;

    public SimulatedTrip(TripData tripData) {
      this.tripData = tripData;
      this.currentStatus = tripData.tripStatus;

      // Initially, set vehicle at first waypoint
      TripWaypoint firstWaypoint = tripData.remainingWaypoints.get(0);
      this.vehicleLocation = new VehicleLocation(
          firstWaypoint.location, 0, System.currentTimeMillis());

      // Set initial remaining distance and ETA
      this.remainingDistanceMeters = 2500; // 2.5 km
      this.etaTimestampMillis = System.currentTimeMillis() + (15 * 60 * 1000); // 15 minutes from now
    }

    // Move the vehicle along simulated path
    public void updateVehicleLocation() {
      if (currentStatus == 4 || currentStatus == 5) { // COMPLETED or CANCELED
        return;
      }

      // Decrease remaining distance
      remainingDistanceMeters = Math.max(0, remainingDistanceMeters - 50);

      // Update ETA - get closer as we approach
      long currentTime = System.currentTimeMillis();
      long remainingTime = Math.max(60000, etaTimestampMillis - currentTime); // at least 1 minute
      etaTimestampMillis = currentTime + (long)(remainingTime * 0.95); // reduce by 5%

      // Update vehicle location - move closer to next waypoint
      if (currentWaypointIndex < tripData.remainingWaypoints.size()) {
        TripWaypoint targetWaypoint = tripData.remainingWaypoints.get(currentWaypointIndex);

        // Move toward the target waypoint
        LatLng currentLoc = vehicleLocation.location;
        LatLng targetLoc = targetWaypoint.location;

        // Simple linear interpolation toward target
        double moveFactor = 0.05; // Move 5% closer each update
        double newLat = currentLoc.lat + (targetLoc.lat - currentLoc.lat) * moveFactor;
        double newLng = currentLoc.lng + (targetLoc.lng - currentLoc.lng) * moveFactor;

        // Calculate heading (direction) - simplified
        double heading = Math.atan2(targetLoc.lng - currentLoc.lng, targetLoc.lat - currentLoc.lat) * 180 / Math.PI;
        if (heading < 0) heading += 360;

        // Update vehicle location
        vehicleLocation = new VehicleLocation(
            new LatLng(newLat, newLng), heading, System.currentTimeMillis());

        // Check if we've arrived at the waypoint
        double distance = Math.sqrt(
            Math.pow(newLat - targetLoc.lat, 2) +
            Math.pow(newLng - targetLoc.lng, 2));

        if (distance < 0.0002) { // Approximately 20 meters
          // We've arrived at the waypoint
          if (targetWaypoint.waypointType == 0) { // PICKUP
            currentStatus = 2; // ARRIVED_AT_PICKUP

            // After a short delay, update status to ENROUTE_TO_DROPOFF and move to next waypoint
            if (remainingDistanceMeters < 100) {
              currentStatus = 3; // ENROUTE_TO_DROPOFF
              currentWaypointIndex++;

              // Reset distance/ETA for next leg
              if (currentWaypointIndex < tripData.remainingWaypoints.size()) {
                remainingDistanceMeters = 3000; // 3 km to next waypoint
                etaTimestampMillis = System.currentTimeMillis() + (20 * 60 * 1000); // 20 min
              }
            }
          } else if (targetWaypoint.waypointType == 1) { // DROPOFF
            // We've arrived at the final destination
            currentStatus = 4; // COMPLETED
            remainingDistanceMeters = 0;
          } else { // INTERMEDIATE
            // Move to next waypoint
            currentWaypointIndex++;

            // Reset distance/ETA for next leg
            if (currentWaypointIndex < tripData.remainingWaypoints.size()) {
              remainingDistanceMeters = 2000; // 2 km to next waypoint
              etaTimestampMillis = System.currentTimeMillis() + (10 * 60 * 1000); // 10 min
            }
          }
        }
      }

      // Update trip data status to match current status
      tripData.tripStatus = currentStatus;
    }
  }

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
   * Initialize the journey sharing module with provider ID and optional token
   */
  @ReactMethod
  public void init(String providerId, @Nullable String providerToken, final Promise promise) {
    Log.d(TAG, "Initializing JourneySharing with providerId: " + providerId);

    this.providerId = providerId;
    this.providerToken = providerToken;

    // In a real implementation, we would initialize the Fleet Engine SDK here
    // For now, we'll just simulate successful initialization

    // Return success
    promise.resolve(true);
  }

  /**
   * Set or update the provider token
   */
  @ReactMethod
  public void setProviderToken(String token, final Promise promise) {
    Log.d(TAG, "Setting provider token");

    this.providerToken = token;

    // In a real implementation, we would update the token in the Fleet Engine SDK

    // Return success
    promise.resolve(true);
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

    try {
      // Parse trip data from JavaScript
      String tripName = tripDataMap.getString("tripName");
      this.tripId = tripName; // Use tripName as tripId for simplicity

      int tripStatus = tripDataMap.getInt("tripStatus");
      String vehicleTypeId = tripDataMap.hasKey("vehicleTypeId") ?
          tripDataMap.getString("vehicleTypeId") : "default";
      String bookingId = tripDataMap.hasKey("bookingId") ?
          tripDataMap.getString("bookingId") : tripName;

      // Parse waypoints
      List<TripWaypoint> waypoints = new ArrayList<>();
      if (tripDataMap.hasKey("remainingWaypoints")) {
        ReadableArray waypointsArray = tripDataMap.getArray("remainingWaypoints");
        for (int i = 0; i < waypointsArray.size(); i++) {
          ReadableMap waypointMap = waypointsArray.getMap(i);

          // Get location
          ReadableMap locationMap = waypointMap.getMap("location");
          double lat = locationMap.getDouble("lat");
          double lng = locationMap.getDouble("lng");
          LatLng location = new LatLng(lat, lng);

          // Get waypoint type (default to PICKUP if not provided)
          int waypointType = waypointMap.hasKey("waypointType") ?
              waypointMap.getInt("waypointType") : 0;

          // Get trip ID (default to parent trip ID if not provided)
          String waypointTripId = waypointMap.hasKey("tripId") ?
              waypointMap.getString("tripId") : tripName;

          // Get title (default to type-based title if not provided)
          String title = waypointMap.hasKey("title") ?
              waypointMap.getString("title") :
              (waypointType == 0 ? "Pickup" : waypointType == 1 ? "Dropoff" : "Stop");

          // Create and add waypoint
          TripWaypoint waypoint = new TripWaypoint(location, waypointType, waypointTripId, title);
          waypoints.add(waypoint);
        }
      }

      // Create trip data object
      TripData tripData = new TripData(tripName, tripStatus, waypoints, vehicleTypeId, bookingId);

      // In a real implementation, we would call Fleet Engine SDK here
      // For now, create a simulated trip for demonstration
      this.simulatedTrip = new SimulatedTrip(tripData);

      // Mark session as active
      isSessionActive = true;

      // Start periodic updates for simulation
      startPeriodicUpdates();

      // Return success
      promise.resolve(true);
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

    // In a real implementation, we would call Fleet Engine SDK to stop journey sharing

    // Stop simulation
    isSessionActive = false;
    simulatedTrip = null;

    // Return success
    promise.resolve(true);
  }

  /**
   * Clean up resources
   */
  @ReactMethod
  public void cleanup(final Promise promise) {
    Log.d(TAG, "Cleaning up journey sharing");

    // Stop journey sharing if active
    if (isSessionActive) {
      isSessionActive = false;
      simulatedTrip = null;
    }

    // In a real implementation, we would clean up Fleet Engine SDK resources

    // Return success
    promise.resolve(true);
  }

  // For simulation only: start periodic updates
  private void startPeriodicUpdates() {
    new Thread(() -> {
      while (isSessionActive && simulatedTrip != null) {
        try {
          // Update simulated trip
          simulatedTrip.updateVehicleLocation();

          // Send updates
          sendTripStatusUpdate();
          sendVehicleLocationUpdate();
          sendETAUpdate();
          sendDistanceUpdate();

          // Wait before next update
          Thread.sleep(2000); // Update every 2 seconds
        } catch (InterruptedException e) {
          Log.e(TAG, "Simulation interrupted", e);
          break;
        } catch (Exception e) {
          Log.e(TAG, "Error in simulation", e);
        }
      }
    }).start();
  }

  // Send trip status update to React Native
  private void sendTripStatusUpdate() {
    if (!isSessionActive || simulatedTrip == null) return;

    WritableMap tripInfo = createTripInfoMap();
    int status = simulatedTrip.currentStatus;

    WritableNativeArray params = new WritableNativeArray();
    params.pushMap(tripInfo);
    params.pushInt(status);

    sendCommandToReactNative("onTripStatusUpdated", params);
  }

  // Send vehicle location update to React Native
  private void sendVehicleLocationUpdate() {
    if (!isSessionActive || simulatedTrip == null) return;

    WritableMap tripInfo = createTripInfoMap();
    WritableMap locationMap = Arguments.createMap();

    VehicleLocation vehicle = simulatedTrip.vehicleLocation;
    locationMap.putDouble("latitude", vehicle.location.lat);
    locationMap.putDouble("longitude", vehicle.location.lng);
    locationMap.putDouble("heading", vehicle.heading);
    locationMap.putDouble("timestamp", vehicle.timestamp);

    WritableNativeArray params = new WritableNativeArray();
    params.pushMap(tripInfo);
    params.pushMap(locationMap);

    sendCommandToReactNative("onTripVehicleLocationUpdated", params);
  }

  // Send ETA update to React Native
  private void sendETAUpdate() {
    if (!isSessionActive || simulatedTrip == null) return;

    WritableMap tripInfo = createTripInfoMap();
    long etaTimestamp = simulatedTrip.etaTimestampMillis;

    WritableNativeArray params = new WritableNativeArray();
    params.pushMap(tripInfo);
    params.pushDouble(etaTimestamp);

    sendCommandToReactNative("onTripETAToNextWaypointUpdated", params);
  }

  // Send distance update to React Native
  private void sendDistanceUpdate() {
    if (!isSessionActive || simulatedTrip == null) return;

    WritableMap tripInfo = createTripInfoMap();
    double distance = simulatedTrip.remainingDistanceMeters;

    WritableNativeArray params = new WritableNativeArray();
    params.pushMap(tripInfo);
    params.pushDouble(distance);

    sendCommandToReactNative("onTripActiveRouteRemainingDistanceUpdated", params);
  }

  // Create trip info map for events
  private WritableMap createTripInfoMap() {
    WritableMap tripInfo = Arguments.createMap();

    if (simulatedTrip != null) {
      TripData tripData = simulatedTrip.tripData;
      tripInfo.putString("tripId", tripData.tripName);
      tripInfo.putInt("tripStatus", tripData.tripStatus);

      // Add waypoints
      if (tripData.remainingWaypoints != null && !tripData.remainingWaypoints.isEmpty()) {
        WritableArray waypointsArray = Arguments.createArray();
        for (TripWaypoint waypoint : tripData.remainingWaypoints) {
          WritableMap waypointMap = Arguments.createMap();
          waypointMap.putString("id", waypoint.id);

          WritableMap locationMap = Arguments.createMap();
          locationMap.putDouble("lat", waypoint.location.lat);
          locationMap.putDouble("lng", waypoint.location.lng);
          waypointMap.putMap("location", locationMap);

          waypointMap.putInt("waypointType", waypoint.waypointType);
          waypointMap.putString("tripId", waypoint.tripId);
          waypointMap.putString("title", waypoint.title);

          waypointsArray.pushMap(waypointMap);
        }
        tripInfo.putArray("remainingWaypoints", waypointsArray);
      }
    }

    return tripInfo;
  }

  // Send command to React Native
  private void sendCommandToReactNative(String functionName, NativeArray params) {
    ReactContext reactContext = getReactApplicationContext();

    if (reactContext != null) {
      CatalystInstance catalystInstance = reactContext.getCatalystInstance();
      catalystInstance.callFunction("JourneySharingModule", functionName, params);
    }
  }
}
