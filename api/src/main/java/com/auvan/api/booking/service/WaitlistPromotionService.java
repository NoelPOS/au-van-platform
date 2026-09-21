package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Offers seats that have come free to the students waiting for them.
 *
 * <p><strong>A sweep, not a hook on the places that free a seat</strong>, and
 * that is the load-bearing decision of ADR-011. Three of those places run code
 * — {@code BookingService.cancel}, {@link BookingExpiryWriter#expire} and
 * {@code SeatHoldService.release} — and the fourth, an abandoned hold, runs
 * none at all, because ADR-006 made expiry lazy on purpose. That fourth is the
 * commonest release of all, so hooking the other three would need a sweep
 * anyway and would ship four places that can each drift. One sweep derives what
 * is free and covers all four.
 *
 * <p><strong>Not {@code @Transactional}, and it must not be.</strong> Each
 * entry is promoted or ended in a transaction of its own, so one that loses its
 * race neither rolls back nor blocks the rest of the batch. The candidate reads
 * are outside all of them and decide nothing:
 * {@link WaitlistPromotionWriter} re-reads behind the row lock and only that
 * reading counts.
 *
 * <p>This runs with no security context. Every query it issues is scoped by the
 * entry it locked and never by a caller, and anything added here has to keep
 * that true.
 */
@Service
public class WaitlistPromotionService {
    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionService.class);

    private final WaitlistEntryRepository entries;
    private final WaitlistPromotionWriter writer;
    private final BookingProperties properties;

    public WaitlistPromotionService(WaitlistEntryRepository entries, WaitlistPromotionWriter writer,
                                    BookingProperties properties) {
        this.entries = entries;
        this.writer = writer;
        this.properties = properties;
    }

    /**
     * Ends the promotions whose windows have run out, then offers the free
     * seats to whoever is next in line.
     *
     * <p>In that order, and the order matters: a promotion that has lapsed has
     * already stopped blocking its seat, so resolving it first means the entry
     * is closed and its student told before anybody else is offered the seat
     * they lost. Nothing chains one promotion to the next — each half derives
     * its own candidates from the database, which is what makes a sweep that
     * dies halfway through simply resume on the next pass.
     *
     * @return how many entries this call promoted, which is fewer than the
     *         batch whenever a seat went to somebody else first
     */
    public int sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        int ended = resolveLapsedPromotions(now);
        int promoted = promoteWaiting(now);
        if (ended > 0) {
            log.info("Ended {} lapsed waitlist promotion(s).", ended);
        }
        if (promoted > 0) {
            log.info("Promoted {} waitlisted student(s) into a seat hold.", promoted);
        }
        return promoted;
    }

    private int resolveLapsedPromotions(OffsetDateTime now) {
        int ended = 0;
        for (UUID entryId : entries.findLapsedPromotionIds(now, batch())) {
            if (writer.resolve(entryId, now)) {
                ended++;
            }
        }
        return ended;
    }

    private int promoteWaiting(OffsetDateTime now) {
        int promoted = 0;
        for (UUID tripId : entries.findPromotableTripIds(now, batch())) {
            promoted += promoteOnto(tripId, now);
        }
        return promoted;
    }

    /**
     * Walks one trip's queue in join order and stops at the first entry it
     * could not promote.
     *
     * <p>Stopping is the FIFO rule, not an optimisation. The commonest reason
     * an entry is not promoted is that the free seats have run out — including
     * the case where one seat is free and the head of the queue is waiting for
     * two — and going on to the next entry would hand a seat to somebody behind
     * them in the queue. The other reasons are all races, and for those the
     * next pass re-derives everything from scratch a poll interval later.
     */
    private int promoteOnto(UUID tripId, OffsetDateTime now) {
        int promoted = 0;
        for (UUID entryId : entries.findNextWaiting(tripId, batch())) {
            try {
                if (!writer.promote(entryId, now)) {
                    break;
                }
            } catch (PromotionRaceLostException raced) {
                // Somebody took the seat between the availability read and the
                // insert. The entry is untouched, so the next sweep tries again.
                log.debug("A promotion on trip {} lost its seats to another claim.", tripId, raced);
                break;
            }
            promoted++;
        }
        return promoted;
    }

    private Pageable batch() {
        return PageRequest.of(0, properties.waitlist().batchSize());
    }
}
