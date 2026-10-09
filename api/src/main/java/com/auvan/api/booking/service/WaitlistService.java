package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.dto.WaitlistEntryResponse;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.live.dto.LiveSignal;
import com.auvan.api.live.service.LiveSignalPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WaitlistService {
    private final WaitlistEntryRepository entries;
    private final TripRepository trips;
    private final SeatClaimRepository claims;
    private final SeatAvailabilityService availability;
    private final BookingEligibilityService eligibility;
    private final BookingProperties properties;
    private final LiveSignalPublisher live;

    public WaitlistService(WaitlistEntryRepository entries, TripRepository trips, SeatClaimRepository claims,
                           SeatAvailabilityService availability, BookingEligibilityService eligibility,
                           BookingProperties properties, LiveSignalPublisher live) {
        this.entries = entries;
        this.trips = trips;
        this.claims = claims;
        this.availability = availability;
        this.eligibility = eligibility;
        this.properties = properties;
        this.live = live;
    }

    @Transactional
    public WaitlistEntryResponse join(UUID userId, JoinWaitlistRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        Trip trip = trips.findById(request.tripId())
                .orElseThrow(() -> Problems.notFound("trip_not_found", "Trip not found."));
        assertJoinable(userId, trip, request.seatsWanted(), now);

        WaitlistEntry entry = entries.findByTripIdAndUserId(trip.getId(), userId).orElse(null);
        if (entry == null) {
            entry = insert(trip, userId, request.seatsWanted(), now);
        } else if (!entry.isQueued()) {
            entry.rejoin(request.seatsWanted(), now);
        }
        return respond(entry);
    }

    private WaitlistEntry insert(Trip trip, UUID userId, int seatsWanted, OffsetDateTime now) {
        try {
            WaitlistEntry inserted = entries.save(new WaitlistEntry(trip, userId, seatsWanted, now));
            // Flush inside the try: @UuidGenerator defers the insert past this catch. Never
            // re-read after a failed flush: the pending insert is re-issued and fails again.
            entries.flush();
            return inserted;
        } catch (DataIntegrityViolationException exception) {
            throw Problems.conflict("waitlist_join_raced",
                    "You are already on this waitlist. Please refresh to see your place.", exception);
        }
    }

    @Transactional
    public void leave(UUID userId, UUID entryId) {
        WaitlistEntry entry = entries.lockByIdAndUserId(entryId, userId)
                .orElseThrow(() -> Problems.notFound("waitlist_entry_not_found", "Waitlist entry not found."));
        UUID promotionHoldId = entry.getPromotionHoldId();
        entry.withdraw(OffsetDateTime.now());
        if (promotionHoldId != null) {
            live.publish(LiveSignal.trip(entry.getTrip().getId()));
            release(promotionHoldId);
        }
    }

    private void release(UUID promotionHoldId) {
        List<UUID> held = claims.findByHoldId(promotionHoldId).stream().map(SeatClaim::getId).toList();
        if (held.isEmpty()) {
            return;
        }
        // Flush before the delete: it clears the context and would discard the withdrawal.
        entries.flush();
        claims.deleteByIdIn(held);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> mine(UUID userId) {
        return entries.findQueuedByUserId(userId).stream().map(this::respond).toList();
    }

    private void assertJoinable(UUID userId, Trip trip, int seatsWanted, OffsetDateTime now) {
        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw Problems.conflict("trip_not_available", "This trip is no longer available.");
        }
        if (!trip.getDepartureAt().isAfter(now)) {
            throw Problems.conflict("trip_departed", "This trip has already departed.");
        }
        if (!now.isBefore(properties.bookingClosesAt(trip.getDepartureAt()))) {
            throw Problems.conflict("booking_closed", "Booking for this trip has closed. Seats can be booked until "
                    + properties.closesBeforeDeparture().toMinutes() + " minutes before departure.");
        }
        eligibility.assertNotBookedOn(userId, trip);
        if (seatsWanted > properties.maxSeatsPerHold()) {
            throw Problems.badRequest("too_many_seats",
                    "You can hold at most " + properties.maxSeatsPerHold() + " seats at a time.");
        }
        if (hasFreeSeatsOn(trip, now)) {
            throw Problems.conflict("waitlist_not_needed",
                    "This trip still has seats. Book one instead of joining the waitlist.");
        }
    }

    private boolean hasFreeSeatsOn(Trip trip, OffsetDateTime now) {
        return !availability.freeSeatsOf(trip, now).isEmpty();
    }

    private WaitlistEntryResponse respond(WaitlistEntry entry) {
        int position = 0;
        for (WaitlistEntry queued : entries.findByTripIdOrderByJoinedAt(entry.getTrip().getId())) {
            if (!queued.isQueued()) {
                continue;
            }
            position++;
            if (queued.getId().equals(entry.getId())) {
                return WaitlistEntryResponse.from(entry, position);
            }
        }
        return WaitlistEntryResponse.from(entry, null);
    }
}
