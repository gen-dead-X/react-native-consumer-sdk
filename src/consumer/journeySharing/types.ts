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

export enum TripStatus {
  NEW,
  ENROUTE_TO_PICKUP,
  ARRIVED_AT_PICKUP,
  ENROUTE_TO_DROPOFF,
  COMPLETED,
  CANCELED,
}

export enum WaypointType {
  PICKUP,
  DROPOFF,
  INTERMEDIATE,
}

export interface LatLng {
  lat: number;
  lng: number;
}

export interface TripWaypoint {
  id?: string;
  location: LatLng;
  waypointType: WaypointType;
  tripId?: string;
  title?: string;
}

export interface VehicleLocation {
  latitude: number;
  longitude: number;
  heading: number;
  timestamp: number;
}

export interface TripInfo {
  tripId: string;
  tripStatus: TripStatus;
  remainingWaypoints?: TripWaypoint[];
  vehicleId?: string;
}

export interface TripData {
  tripName: string;
  tripStatus: TripStatus | string;
  remainingWaypoints: TripWaypoint[];
  vehicleTypeId?: string;
  bookingId?: string;
}

export interface JourneySharingListeners {
  onTripStatusUpdated?: (tripInfo: TripInfo, status: TripStatus) => void;
  onTripETAToNextWaypointUpdated?: (
    tripInfo: TripInfo,
    timestampMillis?: number
  ) => void;
  onTripVehicleLocationUpdated?: (
    tripInfo: TripInfo,
    vehicleLocation?: VehicleLocation
  ) => void;
  onTripRemainingWaypointsUpdated?: (
    tripInfo: TripInfo,
    waypointList?: TripWaypoint[]
  ) => void;
  onTripActiveRouteRemainingDistanceUpdated?: (
    tripInfo: TripInfo,
    distanceMeters?: number
  ) => void;
}

export interface JourneySharingController {
  init: (providerId: string, token?: string) => Promise<boolean>;
  setProviderToken: (token: string) => Promise<boolean>;
  startJourneySharing: (tripData: TripData) => Promise<boolean>;
  stopJourneySharing: () => Promise<boolean>;
  cleanup: () => Promise<boolean>;
}
