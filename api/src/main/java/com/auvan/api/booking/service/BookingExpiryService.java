package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Releases the seats of bookings that were never paid for.
 *
 * <p>ADR-009 left a booking in {@code PENDING_PAYMENT},
 * {@code PAYMENT_UNDER_REVIEW} or {@code PAYMENT_REJECTED} holding its seats
 * forever — {@code V3}'s own comment says a claim blocks its seat while
 * {@code booking_id IS NOT NULL OR expires_at > now}, so a claim that carries a
 * booking has no expiry left in it at all. This is the bound ADR-010 decided.
 *
 * <p><strong>Not {@code @Transactional}, and it must not be.</strong> Each
 * booking is expired in a transaction of its own, so one that loses its race
 * neither rolls back nor blocks the rest of the batch. The candidate read is
 * outside all of them and decides nothing: {@link BookingExpiryWriter#expire}
 * re-reads behind the row lock and only that reading counts.
 *
 * <p>This runs with no security context. Every query it issues is scoped by the
 * booking it locked and never by a caller, and anything added here has to keep
 * that true.
 */
@Service
public class BookingExpiryService {
    private static final Logger log = LoggerFactory.getLogger(BookingExpiryService.class);

    private final BookingRepository bookings;
    private final BookingExpiryWriter writer;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final BookingProperties properties;

    public BookingExpiryService(BookingRepository bookings, BookingExpiryWriter writer,
                                IdempotencyKeyRepository idempotencyKeys, BookingProperties properties) {
        this.bookings = bookings;
        this.writer = writer;
        this.idempotencyKeys = idempotencyKeys;
        this.properties = properties;
    }

    /**
     * Expires one batch of overdue bookings and prunes spent idempotency keys.
     *
     * @return how many bookings this call expired, which is fewer than the
     *         batch whenever another transaction got to one first
     */
    public int sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> due = bookings.findExpirable(now, PageRequest.of(0, properties.expiry().batchSize()));
        int expired = 0;
        for (UUID bookingId : due) {
            if (writer.expire(bookingId, now)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid booking(s) and released their seats.", expired);
        }
        prune(now);
        return expired;
    }

    /**
     * ADR-008 accepted that {@code idempotency_keys} grows without bound and
     * named this sweep as where the prune would live. It is here rather than on
     * a timer of its own because a second scheduled thing is a second thing to
     * get wrong, and this one already runs on a short interval.
     */
    private void prune(OffsetDateTime now) {
        int pruned = idempotencyKeys.deleteCreatedBefore(now.minus(properties.idempotencyKeyRetention()));
        if (pruned > 0) {
            log.info("Pruned {} idempotency key(s) past the retention window.", pruned);
        }
    }
}
