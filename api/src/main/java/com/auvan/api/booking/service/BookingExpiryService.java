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

    // Not @Transactional: each booking gets its own transaction, so a lost race spares the batch.
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

    private void prune(OffsetDateTime now) {
        int pruned = idempotencyKeys.deleteCreatedBefore(now.minus(properties.idempotencyKeyRetention()));
        if (pruned > 0) {
            log.info("Pruned {} idempotency key(s) past the retention window.", pruned);
        }
    }
}
