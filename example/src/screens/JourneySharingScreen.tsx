/**
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { useEffect, useState, useCallback } from 'react';
import { View, Button, Text, TextInput, StyleSheet } from 'react-native';
import {
  useJourneySharing,
  TripStatus,
  WaypointType,
  type TripData,
  type TripWaypoint,
} from '@googlemaps/react-native-navigation-sdk';
import axios from 'axios';

interface JourneySharingExampleProps {
  // Optional trip ID to track, if not provided, a UI will be shown to enter one
  tripId?: string;
  // Provider ID for the Fleet Engine integration
  providerId?: string;
  // Optional provider authentication token
  providerToken?: string;
}

/**
 * Example component demonstrating how to use the Journey Sharing API
 *
 * @return {React.ReactElement} The rendered Journey Sharing Example component.
 */
export const JourneySharingExample: React.FC<JourneySharingExampleProps> = ({
  tripId: initialTripId,
  providerId: initialProviderId = 'snap-e-odrd-mobility',
}) => {
  const [tripId, setTripId] = useState<string>(initialTripId || '');
  const [inputTripId, setInputTripId] = useState<string>('KOL9421744792966');
  const [providerId] = useState<string>(initialProviderId);
  const [providerToken, setProviderToken] = useState<string>('');
  const [isSessionActive, setIsSessionActive] = useState<boolean>(false);
  const [isInitialized, setIsInitialized] = useState<boolean>(false);
  const [eta, setEta] = useState<string>('');
  const [distance, setDistance] = useState<string>('');
  const [tripStatus, setTripStatus] = useState<string>('Not started');
  const [error, setError] = useState<string>('');

  const { journeySharingController, addListeners, removeListeners } =
    useJourneySharing();

  // Journey Sharing callbacks
  const journeySharingCallbacks = React.useMemo(
    () => ({
      onTripStatusUpdated: (tripInfo, status) => {
        setTripStatus(TripStatus[status] || 'Unknown');
      },
      onTripETAToNextWaypointUpdated: (tripInfo, timestampMillis) => {
        if (timestampMillis) {
          const etaDate = new Date(timestampMillis);
          setEta(etaDate.toLocaleTimeString());
        }
      },
      onTripActiveRouteRemainingDistanceUpdated: (tripInfo, distanceMeters) => {
        if (distanceMeters) {
          setDistance(
            distanceMeters >= 1000
              ? `${(distanceMeters / 1000).toFixed(1)} km`
              : `${distanceMeters} m`
          );
        }
      },
      onTripVehicleLocationUpdated: (tripInfo, vehicleLocation) => {
        console.log('Vehicle location updated:', vehicleLocation);
      },
    }),
    []
  );

  // Initialize the journey sharing controller
  const initializeJourneySharing = useCallback(async () => {
    try {
      setError('');
      await journeySharingController.init(providerId, providerToken);
      setIsInitialized(true);
    } catch (err) {
      console.error('Failed to initialize journey sharing:', err);
      setError(
        'Failed to initialize: ' +
          (err instanceof Error ? err.message : String(err))
      );
    }
  }, [journeySharingController, providerId, providerToken]);

  // Setup event listeners
  useEffect(() => {
    addListeners(journeySharingCallbacks);

    // Cleanup
    return () => {
      if (isSessionActive) {
        journeySharingController.stopJourneySharing().catch(console.error);
      }
      removeListeners(journeySharingCallbacks);
      journeySharingController.cleanup().catch(console.error);
    };
  }, [
    journeySharingController,
    addListeners,
    removeListeners,
    journeySharingCallbacks,
    isSessionActive,
  ]);

  const startJourneySharing = useCallback(async () => {
    if (!tripId) {
      setError('Trip ID is required');
      return;
    }

    if (!isInitialized) {
      setError('Journey sharing module not initialized');
      return;
    }

    try {
      setError('');

      // Example waypoint structure
      const waypoints: TripWaypoint[] = [
        {
          location: { lat: 37.422, lng: -122.084 },
          waypointType: WaypointType.PICKUP,
          tripId: tripId,
          title: 'Pickup Location',
        },
        {
          location: { lat: 37.42, lng: -122.09 },
          waypointType: WaypointType.DROPOFF,
          tripId: tripId,
          title: 'Dropoff Location',
        },
      ];

      // Example trip data
      const tripData: TripData = {
        tripName: tripId,
        tripStatus: TripStatus[TripStatus.NEW],
        remainingWaypoints: waypoints,
        vehicleTypeId: 'standard',
        bookingId: tripId,
      };

      await journeySharingController.startJourneySharing(tripData);
      setIsSessionActive(true);
    } catch (err) {
      console.error('Error starting journey sharing:', err);
      setError(
        'Failed to start journey sharing: ' +
          (err instanceof Error ? err.message : String(err))
      );
    }
  }, [tripId, journeySharingController, isInitialized]);

  const stopJourneySharing = useCallback(async () => {
    try {
      await journeySharingController.stopJourneySharing();
      setIsSessionActive(false);
      setEta('');
      setDistance('');
      setTripStatus('Stopped');
    } catch (err) {
      console.error('Error stopping journey sharing:', err);
      setError(
        'Failed to stop journey sharing: ' +
          (err instanceof Error ? err.message : String(err))
      );
    }
  }, [journeySharingController]);

  const onSetTripId = useCallback(() => {
    setTripId(inputTripId);
  }, [inputTripId]);

  const onSetProviderToken = useCallback(async () => {
    const token = await getAuthToken();

    console.log({ token });

    if (!token) {
      setError('Failed to fetch auth token');
      return;
    }

    setProviderToken(token);
    journeySharingController.setProviderToken(token).catch(err => {
      console.error('Failed to set provider token:', err);
      setError(
        'Failed to set token: ' +
          (err instanceof Error ? err.message : String(err))
      );
    });
  }, [journeySharingController]);

  useEffect(() => {
    console.log({ providerToken });
  }, [providerToken]);

  /**
   * Fetches the authentication token for the provider.
   * @return {Promise<string | false>} The authentication token or false if fetching fails.
   */
  async function getAuthToken() {
    try {
      const url = `https://api-dev.snapecab.com/v1/token/consumer?bookingId=KOL9421744792966`;

      const headers = {
        language: 'en',
        platform: 2,
      };

      const response = await axios.get(url, { headers });

      if (response.status !== 200) {
        throw new Error('Failed to fetch auth token');
      }

      const data = response.data;

      console.log(data.data.token);

      return data.data.token; // Adjust based on your API response structure
    } catch (fetchError) {
      console.log(fetchError);
      return false;
    }
  }

  return (
    <View style={styles.container}>
      <View style={styles.headerContainer}>
        <Text style={styles.headerText}>Journey Sharing Consumer SDK</Text>
      </View>

      <View style={styles.setupContainer}>
        <Text style={styles.sectionTitle}>Setup</Text>

        <View style={styles.inputContainer}>
          <Text style={styles.inputLabel}>Provider ID:</Text>
          <Text style={styles.value}>{providerId}</Text>
        </View>

        <View style={styles.inputContainer}>
          <Text style={styles.inputLabel}>Provider Token:</Text>
          <TextInput
            style={styles.input}
            placeholder="Enter Provider Token"
            value={providerToken}
            onChangeText={setProviderToken}
          />
          <Button title="Set" onPress={onSetProviderToken} />
        </View>

        <View style={styles.inputContainer}>
          <Text style={styles.inputLabel}>Trip ID:</Text>
          <TextInput
            style={styles.input}
            placeholder="Enter Trip ID"
            value={inputTripId}
            onChangeText={setInputTripId}
          />
          <Button title="Set" onPress={onSetTripId} />
        </View>

        <Button
          title={isInitialized ? 'Re-Initialize' : 'Initialize'}
          onPress={initializeJourneySharing}
        />
      </View>

      <View style={styles.statusContainer}>
        <Text style={styles.sectionTitle}>Status</Text>

        <View style={styles.statusRow}>
          <Text style={styles.label}>Current Trip ID:</Text>
          <Text style={styles.value}>{tripId || 'Not set'}</Text>
        </View>

        <View style={styles.statusRow}>
          <Text style={styles.label}>Trip Status:</Text>
          <Text style={styles.value}>{tripStatus}</Text>
        </View>

        <View style={styles.statusRow}>
          <Text style={styles.label}>ETA:</Text>
          <Text style={styles.value}>{eta || 'N/A'}</Text>
        </View>

        <View style={styles.statusRow}>
          <Text style={styles.label}>Distance Remaining:</Text>
          <Text style={styles.value}>{distance || 'N/A'}</Text>
        </View>
      </View>

      {error ? <Text style={styles.error}>{error}</Text> : null}

      <View style={styles.buttonContainer}>
        <Button
          title="Start Journey Sharing"
          onPress={startJourneySharing}
          disabled={isSessionActive || !tripId || !isInitialized}
        />
        <View style={styles.buttonSpacer} />
        <Button
          title="Stop Journey Sharing"
          onPress={stopJourneySharing}
          disabled={!isSessionActive}
        />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 16,
    flex: 1,
  },
  headerContainer: {
    marginBottom: 16,
    padding: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#ddd',
  },
  headerText: {
    fontSize: 20,
    fontWeight: 'bold',
    textAlign: 'center',
  },
  setupContainer: {
    backgroundColor: '#f8f8f8',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  inputLabel: {
    width: 100,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 4,
    padding: 8,
    marginRight: 8,
  },
  statusContainer: {
    backgroundColor: '#f0f0f0',
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  statusRow: {
    flexDirection: 'row',
    marginBottom: 8,
  },
  label: {
    fontWeight: 'bold',
    width: 140,
  },
  value: {
    flex: 1,
  },
  error: {
    color: 'red',
    marginBottom: 16,
    padding: 8,
    backgroundColor: '#ffeeee',
    borderRadius: 4,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 16,
  },
  buttonSpacer: {
    width: 16,
  },
});
