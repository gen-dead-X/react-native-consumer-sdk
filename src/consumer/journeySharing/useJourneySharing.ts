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

import { useCallback, useEffect, useMemo, useState } from 'react';
import { NativeModules, NativeEventEmitter } from 'react-native';

import {
  JourneySharingController,
  JourneySharingListeners,
  TripStatus,
  TripInfo,
  VehicleLocation,
  TripWaypoint,
} from './types';

const { JourneySharingModule } = NativeModules;
const journeySharingEventEmitter = new NativeEventEmitter(JourneySharingModule);

/**
 * Hook to provide journey sharing functionality
 */
export const useJourneySharing = () => {
  const [isInitialized, setIsInitialized] = useState(false);
  const [isSessionActive, setIsSessionActive] = useState(false);
  const [activeListeners, setActiveListeners] =
    useState<JourneySharingListeners>({});

  // Create controller with methods that communicate with native module
  const journeySharingController: JourneySharingController = useMemo(
    () => ({
      init: async (providerId: string, token?: string) => {
        try {
          const result = await JourneySharingModule.init(providerId, token);
          setIsInitialized(result);
          return result;
        } catch (error) {
          console.error('Failed to initialize journey sharing:', error);
          return false;
        }
      },
      setProviderToken: async (token: string) => {
        try {
          return await JourneySharingModule.setProviderToken(token);
        } catch (error) {
          console.error('Failed to set provider token:', error);
          return false;
        }
      },
      startJourneySharing: async tripData => {
        try {
          const result =
            await JourneySharingModule.startJourneySharing(tripData);
          setIsSessionActive(result);
          return result;
        } catch (error) {
          console.error('Failed to start journey sharing:', error);
          return false;
        }
      },
      stopJourneySharing: async () => {
        try {
          const result = await JourneySharingModule.stopJourneySharing();
          setIsSessionActive(false);
          return result;
        } catch (error) {
          console.error('Failed to stop journey sharing:', error);
          return false;
        }
      },
      cleanup: async () => {
        try {
          const result = await JourneySharingModule.cleanup();
          setIsSessionActive(false);
          setIsInitialized(false);
          return result;
        } catch (error) {
          console.error('Failed to clean up journey sharing:', error);
          return false;
        }
      },
    }),
    []
  );

  // Add event listeners
  const addListeners = useCallback((listeners: JourneySharingListeners) => {
    setActiveListeners(prevListeners => ({
      ...prevListeners,
      ...listeners,
    }));
  }, []);

  // Remove event listeners
  const removeListeners = useCallback((listeners: JourneySharingListeners) => {
    setActiveListeners(prevListeners => {
      const newListeners = { ...prevListeners };
      // Remove each listener that matches
      (Object.keys(listeners) as Array<keyof JourneySharingListeners>).forEach(
        key => {
          if (listeners[key] === prevListeners[key]) {
            delete newListeners[key];
          }
        }
      );
      return newListeners;
    });
  }, []);

  // Set up native event listeners
  useEffect(() => {
    // Trip status updated
    const statusSubscription = journeySharingEventEmitter.addListener(
      'onTripStatusUpdated',
      ([tripInfo, status]) => {
        if (activeListeners.onTripStatusUpdated) {
          activeListeners.onTripStatusUpdated(tripInfo, status);
        }
      }
    );

    // ETA updated
    const etaSubscription = journeySharingEventEmitter.addListener(
      'onTripETAToNextWaypointUpdated',
      ([tripInfo, timestampMillis]) => {
        if (activeListeners.onTripETAToNextWaypointUpdated) {
          activeListeners.onTripETAToNextWaypointUpdated(
            tripInfo,
            timestampMillis
          );
        }
      }
    );

    // Vehicle location updated
    const locationSubscription = journeySharingEventEmitter.addListener(
      'onTripVehicleLocationUpdated',
      ([tripInfo, vehicleLocation]) => {
        if (activeListeners.onTripVehicleLocationUpdated) {
          activeListeners.onTripVehicleLocationUpdated(
            tripInfo,
            vehicleLocation
          );
        }
      }
    );

    // Waypoints updated
    const waypointsSubscription = journeySharingEventEmitter.addListener(
      'onTripRemainingWaypointsUpdated',
      ([tripInfo, waypointList]) => {
        if (activeListeners.onTripRemainingWaypointsUpdated) {
          activeListeners.onTripRemainingWaypointsUpdated(
            tripInfo,
            waypointList
          );
        }
      }
    );

    // Remaining distance updated
    const distanceSubscription = journeySharingEventEmitter.addListener(
      'onTripActiveRouteRemainingDistanceUpdated',
      ([tripInfo, distanceMeters]) => {
        if (activeListeners.onTripActiveRouteRemainingDistanceUpdated) {
          activeListeners.onTripActiveRouteRemainingDistanceUpdated(
            tripInfo,
            distanceMeters
          );
        }
      }
    );

    // Clean up
    return () => {
      statusSubscription.remove();
      etaSubscription.remove();
      locationSubscription.remove();
      waypointsSubscription.remove();
      distanceSubscription.remove();
    };
  }, [activeListeners]);

  return {
    journeySharingController,
    isInitialized,
    isSessionActive,
    addListeners,
    removeListeners,
  };
};
