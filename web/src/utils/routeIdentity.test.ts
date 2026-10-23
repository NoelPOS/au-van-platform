import { describe, expect, it } from "vitest";
import type { Trip, VanRoute } from "../types/inventory";
import {
  identityOf,
  routeIdentities,
  routesIn,
  tripsByRoute,
} from "./routeIdentity";

function route(id: string, origin: string, destination: string): VanRoute {
  return { id, origin, destination, fare: 35, durationMinutes: 45, status: "ACTIVE" };
}

function trip(id: string, routeId: string, departureAt: string): Trip {
  return {
    id,
    routeId,
    vehicleId: "van-1",
    departureAt,
    fare: 35,
    durationMinutes: 45,
    status: "ACTIVE",
    cancellationReason: null,
    seats: [],
  };
}

const toMega = route("to-mega", "AU", "Mega Bangna");
const fromMega = route("from-mega", "Mega Bangna", "AU");
const toSiam = route("to-siam", "AU", "Siam Paragon");
const fromSiam = route("from-siam", "Siam Paragon", "AU");

describe("routeIdentities", () => {
  it("gives both directions of a pair one colour and other pairs their own", () => {
    const identities = routeIdentities([toMega, fromMega, toSiam, fromSiam]);
    const colour = (id: string) => identityOf(identities, id).colour.dot;

    expect(colour("to-mega")).toBe(colour("from-mega"));
    expect(colour("to-siam")).toBe(colour("from-siam"));
    expect(colour("to-mega")).not.toBe(colour("to-siam"));
  });

  it("keeps the same colours whatever order the routes arrive in", () => {
    const one = routeIdentities([toMega, fromMega, toSiam, fromSiam]);
    const other = routeIdentities([fromSiam, toSiam, fromMega, toMega]);

    expect(other).toEqual(one);
    expect(one.map((identity) => identity.routeId)).toEqual([
      "to-mega",
      "from-mega",
      "to-siam",
      "from-siam",
    ]);
  });

  it("names a route by the far end and says which way it runs", () => {
    const identities = routeIdentities([toMega, fromMega, toSiam]);

    expect(identityOf(identities, "to-mega")).toMatchObject({
      short: "Mega Bangna",
      full: "AU → Mega Bangna",
      direction: "out",
    });
    expect(identityOf(identities, "from-mega")).toMatchObject({
      short: "Mega Bangna",
      full: "Mega Bangna → AU",
      direction: "back",
    });
  });

  it("spells out a route that does not touch the busiest stop", () => {
    const across = route("across", "Mega Bangna", "Siam Paragon");
    const identities = routeIdentities([toMega, toSiam, across]);

    expect(identityOf(identities, "across")).toMatchObject({
      short: "Mega Bangna → Siam Paragon",
      direction: null,
    });
  });

  it("labels a route it does not know rather than guessing", () => {
    expect(identityOf([], "gone")).toMatchObject({
      routeId: "gone",
      short: "Unknown route",
      direction: null,
    });
  });
});

describe("tripsByRoute", () => {
  it("groups trips in route order with times ascending inside", () => {
    const identities = routeIdentities([toMega, fromMega]);
    const groups = tripsByRoute(
      [
        trip("back-late", "from-mega", "2026-01-05T11:00:00Z"),
        trip("out-late", "to-mega", "2026-01-05T09:00:00Z"),
        trip("back-early", "from-mega", "2026-01-05T03:00:00Z"),
        trip("out-early", "to-mega", "2026-01-05T01:00:00Z"),
      ],
      identities,
    );

    expect(
      groups.map((group) => [
        group.route.full,
        group.trips.map((entry) => entry.id),
      ]),
    ).toEqual([
      ["AU → Mega Bangna", ["out-early", "out-late"]],
      ["Mega Bangna → AU", ["back-early", "back-late"]],
    ]);
  });
});

describe("routesIn", () => {
  it("keeps only the routes that are used, in route order", () => {
    const identities = routeIdentities([toMega, fromMega, toSiam]);

    expect(
      routesIn(identities, ["to-siam", "to-mega", "to-siam"]).map(
        (identity) => identity.routeId,
      ),
    ).toEqual(["to-mega", "to-siam"]);
  });
});
