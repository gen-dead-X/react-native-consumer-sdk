# Consumer SDK Integration

This document outlines how to integrate the Consumer SDK capabilities into your React Native application using the Google Maps Navigation SDK for React Native.

## Journey Sharing

The journey sharing module leverages Google's Fleet Engine APIs to provide real-time trip tracking and journey sharing capabilities for consumer applications.

### Setup Requirements

To use the journey sharing functionality, you need:

1. A Google Maps Platform project with Fleet Engine APIs enabled
2. A provider ID registered with Fleet Engine
3. Authentication tokens for your trips

### Initialization

First, initialize the journey sharing module with your provider ID and an optional authentication token:

```typescript
import { useJourneySharing } from '@googlemaps/react-native-navigation-sdk';

const { journeySharingController } = useJourneySharing();

// Initialize with provider ID
await journeySharingController.init('your-provider-id');

// Or initialize with provider ID and token
await journeySharingController.init(
  'your-provider-id',
  'your-authentication-token'
);
```

The token can also be set or updated separately:

```typescript
await journeySharingController.setProviderToken('your-updated-token');
```

### Starting a Trip Tracking Session

To start tracking a trip, provide the necessary trip data:

```typescript
import {
  TripStatus,
  WaypointType,
} from '@googlemaps/react-native-navigation-sdk';

const tripData = {
  tripName: 'trip-123',
  tripStatus: TripStatus[TripStatus.NEW],
  remainingWaypoints: [
    {
      location: { lat: 37.422, lng: -122.084 },
      waypointType: WaypointType.PICKUP,
      tripId: 'trip-123',
      title: 'Pickup Location',
    },
    {
      location: { lat: 37.42, lng: -122.09 },
      waypointType: WaypointType.DROPOFF,
      tripId: 'trip-123',
      title: 'Dropoff Location',
    },
  ],
  vehicleTypeId: 'standard',
  bookingId: 'booking-123',
};

await journeySharingController.startJourneySharing(tripData);
```

### Listening for Trip Updates

Register event listeners to receive updates about the trip:

```typescript
import { useJourneySharing } from '@googlemaps/react-native-navigation-sdk';

const { journeySharingController, addListeners, removeListeners } =
  useJourneySharing();

// Register event listeners
const listeners = {
  onTripStatusUpdated: (tripInfo, status) => {
    console.log(`Trip status updated to: ${TripStatus[status]}`);
  },
  onTripETAToNextWaypointUpdated: (tripInfo, timestampMillis) => {
    if (timestampMillis) {
      const etaDate = new Date(timestampMillis);
      console.log(`ETA updated to: ${etaDate.toLocaleTimeString()}`);
    }
  },
  onTripVehicleLocationUpdated: (tripInfo, vehicleLocation) => {
    console.log('Vehicle location updated:', vehicleLocation);
  },
  onTripRemainingWaypointsUpdated: (tripInfo, waypointList) => {
    console.log('Remaining waypoints updated:', waypointList);
  },
  onTripActiveRouteRemainingDistanceUpdated: (tripInfo, distanceMeters) => {
    if (distanceMeters) {
      console.log(`Remaining distance: ${distanceMeters} meters`);
    }
  },
};

// Add listeners
addListeners(listeners);

// When you're done, remove the listeners
removeListeners(listeners);
```

### Stopping Journey Sharing

When the trip is complete or you want to stop tracking:

```typescript
await journeySharingController.stopJourneySharing();
```

### Cleanup Resources

To clean up all resources when the journey sharing feature is no longer needed:

```typescript
await journeySharingController.cleanup();
```

### Using with Real Trips vs. Simulated Trips

The journey sharing module can work with:

1. **Real trips from Fleet Engine**: Provide a valid provider ID and authentication token to track real trips.
2. **Synthetic (simulated) trips**: For testing, you can omit the token and the module will create a synthetic trip based on the provided waypoints.

### Handling Authentication

Since authentication is handled outside the library, you can implement a token refresh mechanism:

```typescript
// Example token refresh
const refreshToken = async () => {
  try {
    const newToken = await yourTokenService.getNewToken();
    await journeySharingController.setProviderToken(newToken);
  } catch (error) {
    console.error('Failed to refresh token:', error);
  }
};

// Set up periodic token refresh
const tokenRefreshInterval = setInterval(refreshToken, 55 * 60 * 1000); // Refresh every 55 minutes

// Clean up interval when done
clearInterval(tokenRefreshInterval);
```

## Example Implementation

See the `JourneySharingExample` component in our example app for a complete implementation example.
