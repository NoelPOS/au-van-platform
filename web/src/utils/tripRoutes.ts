import type { AvailableTrip } from "../types/booking";

export type RouteDepartures = {
  routeId: string;
  origin: string;
  destination: string;
  fare: number;
  durationMinutes: number;
  trips: AvailableTrip[];
};

export function groupByRoute(trips: AvailableTrip[]): RouteDepartures[] {
  const routes = new Map<string, RouteDepartures>();
  for (const trip of trips) {
    const route = routes.get(trip.routeId);
    if (route) {
      route.trips.push(trip);
    } else {
      routes.set(trip.routeId, {
        routeId: trip.routeId,
        origin: trip.origin,
        destination: trip.destination,
        fare: trip.fare,
        durationMinutes: trip.durationMinutes,
        trips: [trip],
      });
    }
  }
  return [...routes.values()].sort((left, right) =>
    left.destination.localeCompare(right.destination),
  );
}

export function nextOpen(
  route: RouteDepartures,
  now: number,
): AvailableTrip | undefined {
  return route.trips
    .filter(
      (trip) =>
        trip.availableSeats > 0 && Date.parse(trip.bookingClosesAt) > now,
    )
    .sort(
      (left, right) =>
        Date.parse(left.departureAt) - Date.parse(right.departureAt),
    )[0];
}
