package com.auvan.api.outbox.repository;

import com.auvan.api.outbox.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The claim and the outcome writes, each its own transaction.
 *
 * <p>{@link com.auvan.api.outbox.service.OutboxDispatcher} is deliberately not
 * transactional — it sends outside any transaction — so every write it makes is
 * annotated here instead. Without that these modifying queries would inherit
 * Spring Data's class-level {@code readOnly} transaction and fail.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    /**
     * The ids of rows that are due, oldest deadline first.
     *
     * <p><strong>Ids, not entities.</strong> Loading the entities here would put
     * them in the persistence context before the claim, and every later read
     * would hand that pre-claim instance back — a row this worker lost to
     * another would still read {@code PENDING} with its old attempt count, and
     * the guard would go on looking exactly like a guard. The same reasoning
     * {@code PaymentProofRepository.findBookingIdById} records for the review
     * path.
     */
    @Query("""
            select event.id from OutboxEvent event
            where event.status in (com.auvan.api.outbox.entity.OutboxStatus.PENDING,
                                   com.auvan.api.outbox.entity.OutboxStatus.IN_FLIGHT)
              and event.nextAttemptAt <= :now
            order by event.nextAttemptAt asc
            """)
    List<UUID> findDispatchable(@Param("now") OffsetDateTime now, Pageable pageable);

    /**
     * Takes the row, or reports that someone else has it. One statement, and
     * the affected-row count is the answer: {@code 1} means this worker holds
     * it, {@code 0} means another worker claimed it first, it has already been
     * resolved, or its lease has not yet run out.
     *
     * <p>Both halves of the {@code WHERE} are load-bearing. {@code status} is
     * what stops a {@code SENT} or {@code DEAD} row being sent again;
     * {@code nextAttemptAt} is what stops two workers holding one row, because
     * the claim pushes that column past {@code now} by a lease. Weaken either
     * and two workers send the same message, which no constraint in this schema
     * would notice.
     *
     * <p>{@code attempts} is incremented by the claim rather than by the
     * outcome, so a worker that dies mid-send still spends an attempt. Without
     * that a row whose send always kills its worker is retried forever.
     */
    @Transactional
    @Modifying
    @Query("""
            update OutboxEvent event
            set event.status = com.auvan.api.outbox.entity.OutboxStatus.IN_FLIGHT,
                event.attempts = event.attempts + 1,
                event.nextAttemptAt = :leaseUntil
            where event.id = :id
              and event.status in (com.auvan.api.outbox.entity.OutboxStatus.PENDING,
                                   com.auvan.api.outbox.entity.OutboxStatus.IN_FLIGHT)
              and event.nextAttemptAt <= :now
            """)
    int claim(@Param("id") UUID id, @Param("now") OffsetDateTime now,
              @Param("leaseUntil") OffsetDateTime leaseUntil);

    /** Terminal success. Only the holder of the claim resolves it, hence the status guard. */
    @Transactional
    @Modifying
    @Query("""
            update OutboxEvent event
            set event.status = com.auvan.api.outbox.entity.OutboxStatus.SENT,
                event.processedAt = :now,
                event.lastError = null
            where event.id = :id and event.status = com.auvan.api.outbox.entity.OutboxStatus.IN_FLIGHT
            """)
    int markSent(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /** Back to the queue, due again once the backoff has elapsed. */
    @Transactional
    @Modifying
    @Query("""
            update OutboxEvent event
            set event.status = com.auvan.api.outbox.entity.OutboxStatus.PENDING,
                event.nextAttemptAt = :nextAttemptAt,
                event.lastError = :error
            where event.id = :id and event.status = com.auvan.api.outbox.entity.OutboxStatus.IN_FLIGHT
            """)
    int markForRetry(@Param("id") UUID id, @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
                     @Param("error") String error);

    /**
     * Terminal failure: the attempt budget is spent. The row stays, carrying
     * its last error, because the point of a dead letter is that somebody can
     * find it. #9 owns surfacing it to an operator.
     */
    @Transactional
    @Modifying
    @Query("""
            update OutboxEvent event
            set event.status = com.auvan.api.outbox.entity.OutboxStatus.DEAD,
                event.processedAt = :now,
                event.lastError = :error
            where event.id = :id and event.status = com.auvan.api.outbox.entity.OutboxStatus.IN_FLIGHT
            """)
    int markDead(@Param("id") UUID id, @Param("now") OffsetDateTime now, @Param("error") String error);
}
