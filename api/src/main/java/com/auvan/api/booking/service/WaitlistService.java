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
import org.springframework.dao.DataIntegrityViolationException;
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
 * simply lapsed with no code running at all (ADR-006, ADR-011).
 * {@link WaitlistPromotionService} is that sweep. The one thing this class owes
 * it is {@link #leave}: a student who walks away from a promotion has to give
 * the seats back.
 */
@Service
public class WaitlistService {
    private final WaitlistEntryRepository entries;
    private final TripRepository trips;
    private final SeatClaimRepository claims;
    private final SeatAvailabilityService availability;
    private final BookingProperties properties;

    public WaitlistService(WaitlistEntryRepository entries, TripRepository trips, SeatClaimRepository claims,
                           SeatAvailabilityService availability, BookingProperties properties) {
        this.entries = entries;
        this.trips = trips;
        this.claims = claims;
        this.availability = availability;
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
            entry = insert(trip, userId, request.seatsWanted(), now);
        } else if (!entry.isQueued()) {
            entry.rejoin(request.seatsWanted(), now);
        }
        // No explicit flush before the queue is read back. The one in insert()
        // is there to make its catch reachable, not to order this read:
        // Hibernate's default AUTO flush mode writes the pending insert or
        // update before a query that touches the same table, so the position
        // below already counts this entry. An explicit flush here would be a
        // mechanism no test defends — removing it turns nothing red, which is
        // the definition of decorative.
        return respond(entry);
    }

    /**
     * Inserts the caller's row, or tells them to refresh if someone inserted it
     * for them first.
     *
     * <p>Two tabs joining at once both miss the lookup above and both insert.
     * {@code waitlist_entries_trip_user_unique} decides that, and the loser's
     * failure has to become a 409 here, because nothing downstream will: there
     * is no advice for {@link DataIntegrityViolationException} in this
     * application, so left alone it reaches the student as a 500 saying the
     * join failed when in fact they are queued. This is the rule
     * {@code SeatHoldService.hold} already follows for {@code seat_claims} —
     * the loser is told to refresh, not handed a crash.
     *
     * <p>The explicit {@code flush} is what makes the catch reachable at all.
     * {@code @UuidGenerator} is not an identity generator, so without it the
     * insert defers to the next flush — the one the position read-back
     * triggers, or the one at commit — both outside this {@code try}.
     *
     * <p>Refreshing is all the loser has to do, because the join is idempotent:
     * their next one finds the winner's row and returns the place they already
     * hold. That read is deliberately not done here. A flush that failed leaves
     * its insert pending in the persistence context, so the next flush in this
     * transaction re-issues it and fails again; answering from a session that
     * can no longer be used would trade this 500 for a less obvious one.
     */
    private WaitlistEntry insert(Trip trip, UUID userId, int seatsWanted, OffsetDateTime now) {
        try {
            WaitlistEntry inserted = entries.save(new WaitlistEntry(trip, userId, seatsWanted, now));
            entries.flush();
            return inserted;
        } catch (DataIntegrityViolationException exception) {
            throw Problems.conflict("waitlist_join_raced",
                    "You are already on this waitlist. Please refresh to see your place.", exception);
        }
    }

    /**
     * Leaves the queue, giving back the seats a promotion may have just handed
     * over. Idempotent: an entry that has already ended stays ended, and the
     * students behind an entry that has just withdrawn move up on their next
     * read, because a position is derived rather than stored.
     *
     * <p>The row is locked first and everything below decides from what the
     * lock returned, because leaving races the promotion sweep: a promotion
     * that committed a moment ago left this student holding seats under
     * {@code promotionHoldId}, and withdrawing without releasing them would
     * leave a hold standing behind an entry that says the student walked away.
     */
    @Transactional
    public void leave(UUID userId, UUID entryId) {
        // Owner-scoped in the query rather than filtered after loading, so
        // someone else's entry answers exactly as one that never existed and the
        // endpoint cannot be used to discover live entry ids — the same idiom
        // SeatHoldService.release uses for a hold.
        WaitlistEntry entry = entries.lockByIdAndUserId(entryId, userId)
                .orElseThrow(() -> Problems.notFound("waitlist_entry_not_found", "Waitlist entry not found."));
        UUID promotionHoldId = entry.getPromotionHoldId();
        entry.withdraw(OffsetDateTime.now());
        if (promotionHoldId != null) {
            release(promotionHoldId);
        }
    }

    /**
     * Releases a promotion's hold, so the seat goes back to the trip rather
     * than staying with a student who has left the queue.
     *
     * <p>{@code deleteByIdIn} refuses a claim that carries a booking, which is
     * the case that matters here: a student who took the seat, booked it, and
     * then left the queue keeps what they paid for. The flush is load-bearing —
     * the delete clears the persistence context, and the withdrawal above would
     * go with it in silence.
     */
    private void release(UUID promotionHoldId) {
        List<UUID> held = claims.findByHoldId(promotionHoldId).stream().map(SeatClaim::getId).toList();
        if (held.isEmpty()) {
            return;
        }
        entries.flush();
        claims.deleteByIdIn(held);
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
     * Whether any seat on the trip is unclaimed right now, through the one
     * derivation of "free" there is: the same
     * {@link SeatAvailabilityService#freeSeatsOf} the promotion sweep promotes
     * onto and the seat map draws itself from. A second, slightly different
     * predicate here would let somebody queue for a trip they could simply
     * book, or queue them behind a seat the sweep thinks is taken.
     */
    private boolean hasFreeSeatsOn(Trip trip, OffsetDateTime now) {
        return !availability.freeSeatsOf(trip, now).isEmpty();
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
