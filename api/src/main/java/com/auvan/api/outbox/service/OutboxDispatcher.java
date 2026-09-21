package com.auvan.api.outbox.service;

import com.auvan.api.notification.service.BookingNotificationHandler;
import com.auvan.api.outbox.config.OutboxProperties;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Claims due outbox rows, sends them, and records what happened.
 *
 * <p><strong>Not {@code @Transactional}, and it must not be called from inside
 * a transaction either.</strong> A send is an HTTP round trip to a third party;
 * holding a database transaction open across it would hold row locks for its
 * duration, which is the very thing recording the work into the transaction
 * exists to avoid. So a dispatch is three transactions: the claim, then the
 * send outside all of them, then the outcome. {@code BookingService}'s own
 * class javadoc records the same shape for the same reason.
 *
 * <p>A worker that dies between the claim and the outcome leaves the row
 * {@code IN_FLIGHT} with its {@code nextAttemptAt} a lease into the future. No
 * sweeper collects it: the lease simply runs out and the row becomes due again.
 * That is SQS's visibility timeout expressed in one table, which is what makes
 * moving to SQS later a change of trigger rather than a rewrite.
 *
 * <p>Nothing here has a security context. Every query is scoped by the row this
 * worker claimed and never by a caller, and anything added to this package has
 * to keep that true.
 */
@Service
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository events;
    private final BookingNotificationHandler handler;
    private final OutboxProperties properties;

    public OutboxDispatcher(OutboxEventRepository events, BookingNotificationHandler handler,
                            OutboxProperties properties) {
        this.events = events;
        this.handler = handler;
        this.properties = properties;
    }

    /**
     * Dispatches one batch of due rows and returns how many were sent.
     *
     * <p>The candidate read is only a hint — a row it names may be claimed by
     * another worker a moment later — so nothing is decided from it. The claim
     * decides.
     */
    public int dispatchBatch() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> due = events.findDispatchable(now, PageRequest.of(0, properties.batchSize()));
        int sent = 0;
        for (UUID id : due) {
            if (dispatch(id, now)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean dispatch(UUID id, OffsetDateTime now) {
        if (events.claim(id, now, now.plus(properties.lease())) == 0) {
            // Another worker holds it, or it has already been resolved. Not an
            // error: this is the claim doing its job.
            return false;
        }
        // Read only after the claim, so the attempt count and the status are
        // the ones the claim wrote rather than the ones the candidate read saw.
        OutboxEvent claimed = events.findById(id).orElseThrow();
        try {
            handler.handle(claimed);
            events.markSent(id, OffsetDateTime.now());
            return true;
        } catch (RuntimeException failure) {
            recordFailure(claimed, failure);
            return false;
        }
    }

    /**
     * A send that threw may still have arrived, so the retry has to be safe
     * rather than avoided: the row keeps its id, and the id is the retry key.
     *
     * <p>Unless retrying is pointless. A {@link PermanentFailureException} says
     * the send will fail the same way however often it is tried — an unknown
     * LINE recipient, or a student who has never added the official account —
     * and it dies here rather than four attempts later. Without that, every such
     * student costs {@code outbox.max-attempts} failed sends for every
     * notification they are owed, forever.
     */
    private void recordFailure(OutboxEvent claimed, RuntimeException failure) {
        String error = truncated(failure.toString());
        if (failure instanceof PermanentFailureException) {
            log.warn("Outbox event {} cannot be delivered and is dead on attempt {}: {}",
                    claimed.getId(), claimed.getAttempts(), error);
            events.markDead(claimed.getId(), OffsetDateTime.now(), error);
            return;
        }
        if (claimed.getAttempts() >= properties.maxAttempts()) {
            log.warn("Outbox event {} failed on attempt {} and is dead: {}",
                    claimed.getId(), claimed.getAttempts(), error);
            events.markDead(claimed.getId(), OffsetDateTime.now(), error);
            return;
        }
        OffsetDateTime nextAttemptAt = OffsetDateTime.now()
                .plus(backoffFor(claimed.getAttempts(), properties.backoffBase(), properties.backoffCap()));
        log.warn("Outbox event {} failed on attempt {}, retrying after {}: {}",
                claimed.getId(), claimed.getAttempts(), nextAttemptAt, error);
        events.markForRetry(claimed.getId(), nextAttemptAt, error);
    }

    /**
     * Doubles from {@code base} with each attempt and stops at {@code cap}, so
     * a dependency that is down is backed off from rather than hammered.
     *
     * <p>Package-private and static because it is arithmetic with a ceiling and
     * an overflow to get wrong, and a unit test says more about it than an
     * integration test that would have to wait out a real delay.
     *
     * @param attempts how many attempts have been made, the claim's own count,
     *                 so the first failure is attempt one and waits {@code base}
     */
    static Duration backoffFor(int attempts, Duration base, Duration cap) {
        // Shifting by 31 or more is undefined for int, and 2^30 base already
        // dwarfs any cap anyone would configure.
        int doublings = Math.min(Math.max(attempts - 1, 0), 30);
        Duration backoff = base.multipliedBy(1L << doublings);
        return backoff.compareTo(cap) > 0 ? cap : backoff;
    }

    /** {@code outbox_events.last_error} is {@code VARCHAR(1000)}; an SDK message can be longer. */
    private static String truncated(String error) {
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
