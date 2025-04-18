/**
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Button, ScrollView } from 'react-native';
import {
  useJourneySharing,
  TripStatus,
  WaypointType,
} from '@googlemaps/react-native-navigation-sdk';

/**
 * Example component demonstrating journey sharing functionality
 */
const JourneySharingExample = () => {
  const {
    journeySharingController,
    isInitialized,
    isSessionActive,
    addListeners,
    removeListeners,
  } = useJourneySharing();

  const [tripStatusText, setTripStatusText] = useState('Not started');
  const [etaText, setEtaText] = useState('Unknown');
  const [distanceText, setDistanceText] = useState('Unknown');
  const [vehicleLocationText, setVehicleLocationText] = useState('Unknown');
  const [logs, setLogs] = useState<string[]>([]);

  // Add a log message
  const addLog = (message: string) => {
    setLogs(prev => [
      `[${new Date().toLocaleTimeString()}] ${message}`,
      ...prev,
    ]);
  };

  // Initialize journey sharing
  const handleInitialize = async () => {
    try {
      // For a real implementation, you would get this from your backend
      const providerId = 'test-provider-id';

      // Initialize without token for synthetic trips
      // For real Fleet Engine trips, you would provide a token here
      const result = await journeySharingController.init(providerId);

      if (result) {
        addLog(`Initialized journey sharing with provider ID: ${providerId}`);
      } else {
        addLog('Failed to initialize journey sharing');
      }
    } catch (error) {
      addLog(`Error initializing: ${error}`);
    }
  };

  // Start journey sharing
  const handleStartJourneySharing = async () => {
    try {
      // Example trip data
      const tripData = {
        tripName: `trip-${Date.now()}`,
        tripStatus: 0, // Use numeric value instead of enum: TripStatus.NEW = 0
        remainingWaypoints: [
          {
            location: { lat: 37.422, lng: -122.084 },
            waypointType: WaypointType.PICKUP,
            title: 'Pickup Location',
          },
          {
            location: { lat: 37.42, lng: -122.09 },
            waypointType: WaypointType.DROPOFF,
            title: 'Dropoff Location',
          },
        ],
        vehicleTypeId: 'standard',
        bookingId: `booking-${Date.now()}`,
      };

      const result =
        await journeySharingController.startJourneySharing(tripData);

      if (result) {
        addLog(`Started journey sharing with trip ID: ${tripData.tripName}`);
      } else {
        addLog('Failed to start journey sharing');
      }
    } catch (error) {
      addLog(`Error starting journey: ${error}`);
    }
  };

  // Stop journey sharing
  const handleStopJourneySharing = async () => {
    try {
      const result = await journeySharingController.stopJourneySharing();

      if (result) {
        addLog('Stopped journey sharing');
        // Reset state
        setTripStatusText('Not started');
        setEtaText('Unknown');
        setDistanceText('Unknown');
        setVehicleLocationText('Unknown');
      } else {
        addLog('Failed to stop journey sharing');
      }
    } catch (error) {
      addLog(`Error stopping journey: ${error}`);
    }
  };

  // Clean up journey sharing
  const handleCleanup = async () => {
    try {
      const result = await journeySharingController.cleanup();

      if (result) {
        addLog('Cleaned up journey sharing');
        // Reset state
        setTripStatusText('Not started');
        setEtaText('Unknown');
        setDistanceText('Unknown');
        setVehicleLocationText('Unknown');
        setLogs([]);
      } else {
        addLog('Failed to clean up journey sharing');
      }
    } catch (error) {
      addLog(`Error cleaning up: ${error}`);
    }
  };

  // Register listeners for trip updates
  useEffect(() => {
    const listeners = {
      onTripStatusUpdated: (tripInfo, status) => {
        setTripStatusText(TripStatus[status]);
        addLog(`Trip status updated to: ${TripStatus[status]}`);
      },
      onTripETAToNextWaypointUpdated: (tripInfo, timestampMillis) => {
        if (timestampMillis) {
          const etaDate = new Date(timestampMillis);
          const etaString = etaDate.toLocaleTimeString();
          setEtaText(etaString);
          addLog(`ETA updated to: ${etaString}`);
        }
      },
      onTripVehicleLocationUpdated: (tripInfo, vehicleLocation) => {
        if (vehicleLocation) {
          const { latitude, longitude, heading } = vehicleLocation;
          const locationString = `Lat: ${latitude.toFixed(5)}, Lng: ${longitude.toFixed(5)}, Heading: ${heading.toFixed(1)}°`;
          setVehicleLocationText(locationString);
          addLog(`Vehicle location updated: ${locationString}`);
        }
      },
      onTripActiveRouteRemainingDistanceUpdated: (tripInfo, distanceMeters) => {
        if (distanceMeters !== undefined) {
          const distanceString = `${(distanceMeters / 1000).toFixed(2)} km`;
          setDistanceText(distanceString);
          addLog(`Remaining distance: ${distanceString}`);
        }
      },
    };

    // Add listeners
    addListeners(listeners);

    // Cleanup function
    return () => {
      removeListeners(listeners);
    };
  }, [addListeners, removeListeners]);

  return (
    <View style={styles.container}>
      <View style={styles.headerContainer}>
        <Text style={styles.headerText}>Journey Sharing Example</Text>
        <Text style={styles.statusText}>
          Initialized: {isInitialized ? 'Yes' : 'No'} | Active:{' '}
          {isSessionActive ? 'Yes' : 'No'}
        </Text>
      </View>

      <View style={styles.controlsContainer}>
        <Button
          title="Initialize"
          onPress={handleInitialize}
          disabled={isInitialized}
        />
        <Button
          title="Start Journey"
          onPress={handleStartJourneySharing}
          disabled={!isInitialized || isSessionActive}
        />
        <Button
          title="Stop Journey"
          onPress={handleStopJourneySharing}
          disabled={!isSessionActive}
        />
        <Button
          title="Cleanup"
          onPress={handleCleanup}
          disabled={!isInitialized}
        />
      </View>

      <View style={styles.infoContainer}>
        <Text style={styles.infoLabel}>Trip Status:</Text>
        <Text style={styles.infoValue}>{tripStatusText}</Text>

        <Text style={styles.infoLabel}>ETA to Next Waypoint:</Text>
        <Text style={styles.infoValue}>{etaText}</Text>

        <Text style={styles.infoLabel}>Remaining Distance:</Text>
        <Text style={styles.infoValue}>{distanceText}</Text>

        <Text style={styles.infoLabel}>Vehicle Location:</Text>
        <Text style={styles.infoValue}>{vehicleLocationText}</Text>
      </View>

      <View style={styles.logContainer}>
        <Text style={styles.logHeader}>Event Log</Text>
        <ScrollView style={styles.logScroll}>
          {logs.map((log, index) => (
            <Text key={index} style={styles.logEntry}>
              {log}
            </Text>
          ))}
        </ScrollView>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: '#F5F5F5',
  },
  headerContainer: {
    marginBottom: 16,
  },
  headerText: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  statusText: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  controlsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  infoContainer: {
    backgroundColor: 'white',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  infoLabel: {
    fontSize: 14,
    color: '#666',
    marginTop: 8,
  },
  infoValue: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 8,
  },
  logContainer: {
    flex: 1,
    backgroundColor: 'white',
    padding: 16,
    borderRadius: 8,
  },
  logHeader: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  logScroll: {
    flex: 1,
  },
  logEntry: {
    fontSize: 12,
    color: '#333',
    marginBottom: 4,
  },
});

export default JourneySharingExample;
