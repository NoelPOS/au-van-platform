# ADR-022: Students Pick a Route First; Staff Keep the Day and See Routes by Colour

## Status

Accepted

## Context

The student app opened on one departure board per day, with every route mixed together by time. With several routes, a student heading to one place had to read past every van going somewhere else, and each row repeated its destination.

The admin side had the same problem. The calendar, the trip list and the payment queue showed one stream of times with no way to tell routes apart at a glance or to look at one route alone. The day's seat plans, bookings and waitlists were on a separate Operations page, so staff found a departure on Trips to plan it and then found it again on Operations to see who was on it.

Students and staff ask different questions. A student asks "when is the next van to where I am going?" Staff ask "what runs on this day, and is each van full?" A van runs out and back over a day, a day template (ADR-019) mixes routes, and *Copy day to…* and *Clear day* act on a whole day.

## Options considered

### Student app

- **Keep the day board with every route on it.** Rejected for the reasons above.
- **Pick a route, then a day and a time.** Chosen. The route list is short, and the choice narrows everything after it.

### Admin views

- **Route-first admin: pick a route, then see its calendar.** Rejected. Staff work by day, and one route at a time would split a van's out-and-back day and a template's lines across several screens.
- **Keep the trip and the day as the unit of work, and group, colour and filter by route.** Chosen.

### Where a route's colour comes from

- **Store a colour on each route.** Rejected for now. It needs a migration, a colour picker in the route form, and a rule that keeps both directions of a pair the same colour. Nothing about a colour needs to survive outside the browser.
- **Hash the route id.** Rejected. The two directions of a pair would get different colours, and two pairs could get the same one.
- **Derive it in the browser from the route list.** Chosen.

### Operations

- **Keep a separate Operations page.** Rejected: staff look up the same departure twice.
- **Fold it into Trips and Overview.** Chosen. Planning and running a departure happen in one place.

## Decision

**Students.** `/` lists the routes that have upcoming departures for the student, built from the available-trips response (`utils/tripRoutes.ts`). Each row shows the destination, the next departure with an open seat, the fare and the travel time. When routes leave from more than one place, the list is grouped by origin. From and To selects narrow it. Picking a route shows that route's day strip and departure board, and each departure shows its arrival time rather than repeating the destination. The choice is kept in the URL as `?route=`, `?from=`, `?to=` and `?day=`, so Back from the seat page returns to the same place. With only one route the list is skipped.

**Staff.** `utils/routeIdentity.ts` derives each route's identity from the admin route list:

- A route's **pair** is its two places, sorted, so both directions share it. Pairs are sorted, and a pair's position picks one of six palette colours, so the colours do not depend on the order the API returns routes in.
- The **hub** is the place that appears in the most routes, with ties broken alphabetically. A route leaving the hub is labelled with its destination and an up-right arrow. A route returning to it is labelled with its origin and a down-left arrow. A route that does not touch the hub shows "Origin → Destination". Screen readers hear "to" or "from" instead of the arrow, and a colour is always next to its label.

The trip calendar uses these everywhere. A month cell lists each place once, folding both directions into one line with a count, and shows three places and "+N more" when there are more than four. Week view tints each departure with its route's colour. List view groups each day by route. Route chips filter Trips and Payments and are kept in `?routes=`. The day drawer is never filtered, because *Copy day to…* and *Clear day* act on the whole day.

**Operations.** Opening a departure on Trips shows a **Passengers** tab (the seat plan, bookings by status and the waitlist) and an **Edit** tab. The Overview lists the notifications given up on under `#notifications`. `/admin/operations` redirects to `/admin/trips`. The API it read from, under `/api/v1/admin/operations`, is unchanged.

## Consequences

- No schema or API change. Payments loads the cached route and trip lists to match each slip's trip to its route.
- Adding a route for a new pair of places can move the colours of pairs that sort after it. A seventh pair reuses the first colour. If staff need fixed or chosen colours, a colour column on `routes` replaces the derivation.
- The hub is inferred from the data. A network with no single busiest stop takes the alphabetically first of the busiest, which may not be the stop staff think of as home.
- A student only sees a route while it has upcoming departures they can see. A route with nothing scheduled is not listed.
- The `/admin/operations` address still works for old bookmarks, and the API path keeps the old name.
