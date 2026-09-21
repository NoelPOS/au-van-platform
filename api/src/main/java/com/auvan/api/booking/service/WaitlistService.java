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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Joining, leaving, and reading a place in a trip's queue.
 *
 * <p>Promotion is not here and is not a hook on anything this class does: it is
 * a scheduled sweep, because the commonest way a seat comes free is a hold that
 * simply lapsed with no code running at all (ADR-006, ADR-011). Issue #69 adds
 * it; nothing on this branch promotes anybody.
 */
@Service
public class WaitlistService {
    private final WaitlistEntryRepository entries;
    private final TripRepository trips;
    private final SeatClaimRepository claims;
    private final BookingProperties properties;

    public WaitlistService(WaitlistEntryRepository entries, TripRepository trips, SeatClaimRepository claims,
                           BookingProperties properties) {
        this.entries = entries;
        this.trips = trips;
        this.claims = claims;
        this.properties = properties;
    }

    /**
     * Puts the caller in the queue, or hands back the place they already have.
     *
     * <p>Joining twice is the same join. The lookup below is what makes that
     * true for a student tapping the button again, and
     * {@code waitlist_entries_trip_user_unique} is what makes it true of the
     * table: there is one row per student per trip, so no path can produce a
     * second one. A student whose entry has <em>ended</em> is re-joined with a
     * fresh {@code joinedAt} and goes to the back, which is the only behaviour
     * a plain unique constraint permits (ADR-011).
     */
    @Transactional
    public WaitlistEntryResponse join(UUID userId, JoinWaitlistRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        Trip trip = trips.findById(request.tripId())
                .orElseThrow(() -> Problems.notFound("trip_not_found", "Trip not found."));
        assertJoinable(trip, request.seatsWanted(), now);

        WaitlistEntry entry = entries.findByTripIdAndUserId(trip.getId(), userId).orElse(null);
        if (entry == null) {
            entry = entries.save(new WaitlistEntry(trip, userId, request.seatsWanted(), now));
        } else if (!entry.isQueued()) {
            entry.rejoin(request.seatsWanted(), now);
        }
        // No explicit flush before the queue is read back. Hibernate's default
        // AUTO flush mode writes the pending insert or update before a query
        // that touches the same table, so the position below already counts this
        // entry. An explicit flush here would be a mechanism no test defends —
        // removing it turns nothing red, which is the definition of decorative.
        return respond(entry);
    }

    /**
     * Leaves the queue. Idempotent: an entry that has already ended stays
     * ended, and the students behind an entry that has just withdrawn move up on
     * their next read, because a position is derived rather than stored.
     */
    @Transactional
    public void leave(UUID userId, UUID entryId) {
        // Owner-scoped in the query rather than filtered after loading, so
        // someone else's entry answers exactly as one that never existed and the
        // endpoint cannot be used to discover live entry ids — the same idiom
        // SeatHoldService.release uses for a hold.
        WaitlistEntry entry = entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> Problems.notFound("waitlist_entry_not_found", "Waitlist entry not found."));
        entry.withdraw(OffsetDateTime.now());
    }

    /** The caller's own queued entries, each with the place it currently holds. */
    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> mine(UUID userId) {
        return entries.findQueuedByUserId(userId).stream().map(this::respond).toList();
    }

    private void assertJoinable(Trip trip, int seatsWanted, OffsetDateTime now) {
        // The same two refusals SeatHoldService.assertBookable makes, with the
        // same codes: a trip nobody can book is a trip nobody should queue for.
        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw Problems.conflict("trip_not_available", "This trip is no longer available.");
        }
        if (!trip.getDepartureAt().isAfter(now)) {
            throw Problems.conflict("trip_departed", "This trip has already departed.");
        }
        if (seatsWanted > properties.maxSeatsPerHold()) {
            throw Problems.badRequest("too_many_seats",
                    "You can hold at most " + properties.maxSeatsPerHold() + " seats at a time.");
        }
        if (hasFreeSeatsOn(trip, now)) {
            throw Problems.conflict("waitlist_not_needed",
                    "This trip still has seats. Book one instead of joining the waitlist.");
        }
    }

    /**
     * Whether any seat on the trip is unclaimed right now, decided by
     * {@link SeatClaim#blocksSeatAt} — the one definition of "free", and the
     * same one {@code SeatAvailabilityService} reads the seat map with. A second,
     * slightly different predicate here would let somebody queue for a trip they
     * could simply book. Issue #69 extracts the derivation so the promoter and
     * the seat map cannot drift; this branch reuses the predicate rather than
     * touching that class.
     */
    private boolean hasFreeSeatsOn(Trip trip, OffsetDateTime now) {
        long claimed = claims.findByTripIdIn(List.of(trip.getId())).stream()
                .filter(claim -> claim.blocksSeatAt(now))
                .count();
        return claimed < trip.getSeats().size();
    }

    /**
     * The entry with its place in the queue, counting only queued entries, so a
     * student who left stops occupying a position for everyone behind them.
     */
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
