import type { Trip, VanRoute } from "../types/inventory";

export type RouteColour = { dot: string; tint: string; edge: string };

export type RouteIdentity = {
  routeId: string;
  short: string;
  full: string;
  direction: "out" | "back" | null;
  colour: RouteColour;
};

const palette: RouteColour[] = [
  {
    dot: "bg-route-1",
    tint: "bg-route-1/8",
    edge: "shadow-[inset_3px_0_0_var(--color-route-1)]",
  },
  {
    dot: "bg-route-2",
    tint: "bg-route-2/8",
    edge: "shadow-[inset_3px_0_0_var(--color-route-2)]",
  },
  {
    dot: "bg-route-3",
    tint: "bg-route-3/8",
    edge: "shadow-[inset_3px_0_0_var(--color-route-3)]",
  },
  {
    dot: "bg-route-4",
    tint: "bg-route-4/8",
    edge: "shadow-[inset_3px_0_0_var(--color-route-4)]",
  },
  {
    dot: "bg-route-5",
    tint: "bg-route-5/8",
    edge: "shadow-[inset_3px_0_0_var(--color-route-5)]",
  },
  {
    dot: "bg-route-6",
    tint: "bg-route-6/8",
    edge: "shadow-[inset_3px_0_0_var(--color-route-6)]",
  },
];

const neutral: RouteColour = {
  dot: "bg-muted/50",
  tint: "",
  edge: "shadow-[inset_3px_0_0_var(--color-line)]",
};

function pairOf(route: VanRoute) {
  return [route.origin, route.destination].sort().join("\n");
}

function hubOf(routes: VanRoute[]) {
  const counts = new Map<string, number>();
  for (const route of routes)
    for (const place of [route.origin, route.destination])
      counts.set(place, (counts.get(place) ?? 0) + 1);
  return [...counts].sort(
    ([left, many], [right, more]) => more - many || left.localeCompare(right),
  )[0]?.[0];
}

function shortName(route: VanRoute, direction: RouteIdentity["direction"]) {
  if (direction === "out") return route.destination;
  if (direction === "back") return route.origin;
  return `${route.origin} → ${route.destination}`;
}

export function routeIdentities(routes: VanRoute[]): RouteIdentity[] {
  const pairs = [...new Set(routes.map(pairOf))].sort();
  const hub = hubOf(routes);
  const rank = (route: VanRoute) =>
    pairs.indexOf(pairOf(route)) * 2 + (route.origin === hub ? 0 : 1);
  return [...routes]
    .sort((left, right) => rank(left) - rank(right))
    .map((route) => {
      const direction =
        route.origin === hub ? "out" : route.destination === hub ? "back" : null;
      return {
        routeId: route.id,
        short: shortName(route, direction),
        full: `${route.origin} → ${route.destination}`,
        direction,
        colour: palette[pairs.indexOf(pairOf(route)) % palette.length],
      };
    });
}

export function identityOf(
  identities: RouteIdentity[],
  routeId: string,
): RouteIdentity {
  return (
    identities.find((identity) => identity.routeId === routeId) ?? {
      routeId,
      short: "Unknown route",
      full: "Unknown route",
      direction: null,
      colour: neutral,
    }
  );
}

export function routesIn(
  identities: RouteIdentity[],
  routeIds: string[],
): RouteIdentity[] {
  const used = new Set(routeIds);
  return identities.filter((identity) => used.has(identity.routeId));
}

export function tripsByRoute(
  trips: Trip[],
  identities: RouteIdentity[],
): { route: RouteIdentity; trips: Trip[] }[] {
  const groups = new Map<string, Trip[]>();
  const ordered = [...trips].sort(
    (left, right) =>
      Date.parse(left.departureAt) - Date.parse(right.departureAt),
  );
  for (const trip of ordered)
    groups.set(trip.routeId, [...(groups.get(trip.routeId) ?? []), trip]);
  const rank = (routeId: string) => {
    const index = identities.findIndex((entry) => entry.routeId === routeId);
    return index < 0 ? identities.length : index;
  };
  return [...groups]
    .sort(([left], [right]) => rank(left) - rank(right))
    .map(([routeId, grouped]) => ({
      route: identityOf(identities, routeId),
      trips: grouped,
    }));
}
