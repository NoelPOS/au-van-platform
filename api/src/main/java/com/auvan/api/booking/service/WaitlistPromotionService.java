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

    // Not @Transactional: each entry gets its own transaction, so a lost race spares the batch.
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

    // Stop at the first entry that cannot be promoted: skipping it would break FIFO.
    private int promoteOnto(UUID tripId, OffsetDateTime now) {
        int promoted = 0;
        for (UUID entryId : entries.findNextWaiting(tripId, batch())) {
            try {
                if (!writer.promote(entryId, now)) {
                    break;
                }
            } catch (PromotionRaceLostException raced) {
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
