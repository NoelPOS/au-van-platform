# Vision and Scope

## Vision

AU-Van replaces manual van bookings with a reliable, LINE-first system where students can reserve seats, submit payment proof, and receive updates, while staff can manage routes, schedules, bookings, payments, and operational exceptions.

## Users

- **Students:** discover schedules, reserve seats, submit payment proof, manage bookings, and receive LINE updates.
- **Staff administrators:** manage routes and timeslots; review payments; manage bookings, users, and exceptions.
- **System operators:** monitor asynchronous jobs, notification failures, expired holds, and audit history.

## MVP scope

- LINE/LIFF student authentication and booking journey.
- Admin portal for routes, timeslots, bookings, users, and payment review.
- Transactionally safe seat holds, booking creation, expiry, cancellation, and rescheduling.
- Manual PromptPay/payment-proof submission and staff approval or rejection.
- LINE notifications for important booking and payment events.
- Audit records for staff-sensitive actions.
- Automated API, integration, E2E, and booking-concurrency coverage.

## Not in the MVP

- Live vehicle tracking.
- Automated payment-gateway settlement.
- Dispatch route optimization.
- Native iOS or Android applications.
- Microservice decomposition.

## Success criteria

- A student can complete a LIFF booking and payment-proof submission.
- A staff member can approve or reject the payment and the student receives the result.
- Concurrent requests cannot create two confirmed bookings for the same seat and timeslot.
- The demo is reproducible locally and deployable with documented, cost-conscious infrastructure.

