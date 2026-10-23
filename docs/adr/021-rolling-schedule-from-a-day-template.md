# ADR-021: Keep the Next Week Scheduled from a Day Template with an In-App Job

## Status

Accepted

## Context

The live demo (ADR-015) only had the departures an administrator had scheduled by hand. Once the last of them departed, a visitor who opened the student app found nothing to book, and so did anyone the owner was showing it to. Day templates (ADR-019) made scheduling a week one step, but someone still had to take that step every week.

The demo needs departures to keep appearing without anyone remembering to add them. Whatever adds them must follow the same rules as an administrator: no past departures, no inactive route or van, no van out on two runs at once, and nothing created twice.

## Options considered

- **Keep scheduling by hand.** Nothing new to build, but the demo runs dry whenever the owner forgets. Rejected.
- **A seed script or cron job on the host, outside the application.** It would write `trips` and `trip_seats` with SQL, outside the planner's clash and availability rules and outside the seat snapshot `Trip` takes from the van's layout. It would also exist only on the demo host. Rejected.
- **A scheduled GitHub Actions workflow calling the admin schedule endpoint.** It goes through the planner, but needs an administrator credential stored in GitHub. Admin tokens last 15 minutes, so that means the demo staff passcode (ADR-020) or a new long-lived token. Rejected.
- **A scheduled job inside the API that reuses the planner.** Chosen. It follows the same rules as an administrator applying a template, and it uses the property-switch pattern the outbox dispatcher and the expiry sweep already use.

## Decision

`RollingScheduleJob` exists only when `inventory.rolling-schedule.enabled` is `true` (`ROLLING_SCHEDULE_ENABLED`). The default is off. `compose.prod.yaml`, the live demo host, turns it on.

It runs once when the application is ready and then at 00:05 Asia/Bangkok every day (`inventory.rolling-schedule.cron`). Each run calls `RollingSchedule.topUp`, which:

1. finds the day template named by `inventory.rolling-schedule.template` (`ROLLING_SCHEDULE_TEMPLATE`, default `Weekday`), ignoring case, and stops if it is missing or has no lines;
2. plans that template's lines across today and the following six Bangkok days (`days-ahead: 7`) with `SchedulePlanner`, the same planner as the admin preview and apply;
3. creates only the departures the planner marks as creatable, in one transaction.

The planner skips a departure that has passed, whose route or van is inactive, or whose van is already out: an active trip whose window overlaps, a cancelled trip at the same instant, or an earlier line of the same plan. A departure the job created on an earlier run is an active trip at the same instant, so it is skipped too. That makes a run idempotent without an `Idempotency-Key` or a `planHash`. There is no preview for anyone to confirm, and `trips_vehicle_departure_unique` is the backstop if two writers race.

A failed run is logged and changes nothing. The next run tries again.

## Consequences

- The demo always has about a week of departures, and a visitor never finds the student app empty while the template exists.
- One template is used for every day, weekends included. A host that needs different plans on different days must turn the job off or correct days by hand.
- The job only adds trips. Removing or moving a template line leaves the trips already created from it. A line added to the template appears on every day in the window at the next run, unless it clashes.
- Clearing a day (ADR-019) deletes its unbooked departures, so the next run puts them back. Cancelling a departure keeps it out, because a cancelled trip at the same instant is a clash.
- A van or route in the template always has upcoming trips, so the in-use guard stops it being made inactive. To retire one, remove it from the template, cancel its upcoming trips, then mark it inactive.
- If an administrator's apply and the job create the same departure at the same moment, the job's transaction loses at `trips_vehicle_departure_unique` and rolls back whole. The next run fills whatever is still missing. If the job commits between an administrator's preview and apply, the apply is refused with `409 schedule_changed`, as for any other change.
- With more than one API instance and the switch on, every instance runs the job. The unique constraint keeps the result correct, but the losers log a failure. The AWS target leaves the switch off.
