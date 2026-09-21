package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One waitlist entry's promotion, or one lapsed promotion's end, in one
 * transaction: the entry's new status, the outbox row that tells the student,
 * and the seat claims commit together or not at all.
 *
 * <p>It is a bean of its own rather than a method on
 * {@link WaitlistPromotionService} for the reason {@link BookingExpiryWriter}
 * is — the sweep must stay outside any transaction so each entry gets its own,
 * and a {@code @Transactional} method called from a sibling method of the same
 * bean would bypass the proxy and quietly run in whatever transaction it found.
 *
 * <p>Both methods open with {@code lockById} and decide only from what the lock
 * returned. The candidate ids they are called with were read outside this
 * transaction and are hints: the student may have left, another sweeper may
 * have promoted them, or they may have booked the seats already.
 */
@Service
public class WaitlistPromotionWriter {
    private static final DateTimeFormatter MOMENT =
            DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private final WaitlistEntryRepository entries;
    private final SeatClaimRepository claims;
    private final SeatAvailabilityService availability;
    private final OutboxRecorder outbox;
    private final BookingProperties properties;

    public WaitlistPromotionWriter(WaitlistEntryRepository entries, SeatClaimRepository claims,
                                   SeatAvailabilityService availability, OutboxRecorder outbox,
                                   BookingProperties properties) {
        this.entries = entries;
        this.claims = claims;
        this.availability = availability;
        this.outbox = outbox;
        this.properties = properties;
    }

    /**
     * Offers this entry the seats it is waiting for, if they are free and it is
     * still waiting once its row is held.
     *
     * <p>A promotion is a <strong>seat hold</strong> under a fresh
     * {@code holdId}, not a booking: ADR-011 rejected booking on the student's
     * behalf because it would need passenger details nobody supplied and commit
     * them to a fare they never agreed to. So the promoted student walks the
     * existing hold, confirm, pay and review path with no special case anywhere
     * in it, and {@code seat_claims_trip_seat_unique} decides a race with a
     * direct booker exactly as it does between two students today.
     *
     * @return whether this call is the one that promoted the entry
     * @throws PromotionRaceLostException if somebody took the seats first,
     *                                    which leaves the entry {@code WAITING}
     *                                    for the next sweep
     */
    @Transactional
    public boolean promote(UUID entryId, OffsetDateTime now) {
        WaitlistEntry entry = entries.lockById(entryId).orElse(null);
        // Not an error, and the ordinary outcome of a lost race: the student
        // left, or another sweeper promoted them, while this sweep was reading
        // its candidates.
        if (entry == null || !entry.isWaiting()) {
            return false;
        }
        Trip trip = entry.getTrip();
        OffsetDateTime expiresAt = properties.promotionDeadlineFor(trip.getDepartureAt(), now);
        // A trip that is no longer active promotes nobody, and neither does one
        // already past departureAt - departure-cutoff: its deadline comes back
        // in the past, and a hold that has expired before it is written helps
        // nobody. ADR-011 chose that over special-casing the trip here.
        if (trip.getStatus() != TripStatus.ACTIVE || !expiresAt.isAfter(now)) {
            return false;
        }

        // The one definition of "free" in this application, shared with the
        // seat map. A second predicate here would promote onto a held seat and
        // the constraint would refuse the insert, so the bug would look like a
        // promotion that silently never happens (ADR-011).
        List<TripSeat> free = availability.freeSeatsOf(trip, now);
        if (free.size() < entry.getSeatsWanted()) {
            return false;
        }
        List<TripSeat> taking = free.subList(0, entry.getSeatsWanted());

        UUID userId = entry.getUserId();
        UUID holdId = UUID.randomUUID();
        entry.promote(holdId, expiresAt, now);
        outbox.record(OutboxEventType.WAITLIST_PROMOTED, entry.getId(), userId,
                new WaitlistNotification(summaryOf(trip), "Take the seats by " + MOMENT.format(expiresAt) + "."),
                now);
        // Load-bearing, and it must come before the reclaim: deleteByIdIn
        // clears the persistence context, which would discard the promotion and
        // the outbox row above without raising anything at all.
        entries.flush();
        reclaim(taking, now);
        insert(taking, userId, holdId, expiresAt);
        return true;
    }

    /**
     * Ends a promotion whose window has run out: {@code FULFILLED} if the
     * student booked the seats they were offered, {@code EXPIRED} if they did
     * nothing.
     *
     * <p>This is where "one chance per promotion" is actually enforced, and it
     * is also the only place an entry becomes {@code FULFILLED}. The booking
     * path is deliberately untouched by the waitlist — ADR-011's whole point is
     * that a promoted hold is an ordinary hold — so the way to find out that a
     * promotion was taken up is to look at what became of its claims.
     *
     * <p>The lapsed hold's own rows are not deleted here. Expiry is lazy
     * (ADR-006): the hold stopped blocking its seat the moment it expired, and
     * the row is reclaimed by whoever next takes the seat, exactly as an
     * abandoned student hold is. Deleting it would duplicate work #62 already
     * does not do and buy nothing.
     *
     * @return whether this call is the one that ended the promotion
     */
    @Transactional
    public boolean resolve(UUID entryId, OffsetDateTime now) {
        WaitlistEntry entry = entries.lockById(entryId).orElse(null);
        if (entry == null || !entry.hasLapsedAt(now)) {
            return false;
        }
        // Booked, so the promotion did its job. No outbox row: the booking's own
        // BOOKING_CREATED already told the student, and a second message saying
        // the same thing a while later is worse than none.
        if (claims.findByHoldId(entry.getPromotionHoldId()).stream().anyMatch(SeatClaim::isBooked)) {
            entry.fulfil(now);
            return true;
        }
        entry.expire(now);
        outbox.record(OutboxEventType.WAITLIST_PROMOTION_EXPIRED, entry.getId(), entry.getUserId(),
                new WaitlistNotification(summaryOf(entry.getTrip()),
                        "The offer ran out on " + MOMENT.format(entry.getPromotionExpiresAt()) + "."), now);
        return true;
    }

    /**
     * Frees the lapsed rows still sitting on the seats this promotion is taking.
     *
     * <p>Necessary, not defensive: {@code seat_claims} holds one row per seat
     * and expiry is lazy, so a seat that reads as free is very often a seat
     * whose lapsed hold is still there. Without this the insert below would
     * collide with a row nobody owns any more and the promotion would silently
     * never happen — which is the commonest release path of all.
     *
     * <p>The bulk delete issues its statement at once, so the reclaim reaches
     * the database before the inserts. Left to a normal flush Hibernate would
     * order the inserts first, exactly as {@code SeatHoldService.hold} records.
     *
     * <p>{@code deleteByIdIn}'s {@code booking_id is null} guard is a
     * correctness guard, not an optimisation: a confirmation can attach a
     * booking to one of these rows between the availability read and this
     * delete, and the guard is what makes the delete refuse to free a seat
     * somebody has just paid for. The insert then loses to the unique
     * constraint, which is the right outcome. Never widen it.
     */
    private void reclaim(List<TripSeat> taking, OffsetDateTime now) {
        List<UUID> lapsed = claims.findBySeatIdIn(taking.stream().map(TripSeat::getId).toList()).stream()
                .filter(claim -> !claim.blocksSeatAt(now))
                .map(SeatClaim::getId)
                .toList();
        if (!lapsed.isEmpty()) {
            claims.deleteByIdIn(lapsed);
        }
    }

    private void insert(List<TripSeat> taking, UUID userId, UUID holdId, OffsetDateTime expiresAt) {
        try {
            claims.saveAll(taking.stream().map(seat -> new SeatClaim(seat, userId, holdId, expiresAt)).toList());
            // Load-bearing. @UuidGenerator is not an identity generator, so
            // without this the insert defers to the commit — long after this
            // catch is out of scope — and a lost race escapes a scheduled sweep
            // as an untranslated failure that nothing is there to catch.
            claims.flush();
        } catch (DataIntegrityViolationException raced) {
            throw new PromotionRaceLostException(raced);
        }
    }

    /** The route and the departure time, which is how a student recognises the trip. */
    private static String summaryOf(Trip trip) {
        return trip.getRoute().getOrigin() + " to " + trip.getRoute().getDestination()
                + ", departing " + MOMENT.format(trip.getDepartureAt()) + ".";
    }
}
