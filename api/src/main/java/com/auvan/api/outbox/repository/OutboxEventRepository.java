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

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    // Ids, not entities: an entity loaded before the claim is handed back stale after it.
    @Query("""
            select event.id from OutboxEvent event
            where event.status in (com.auvan.api.outbox.entity.OutboxStatus.PENDING,
                                   com.auvan.api.outbox.entity.OutboxStatus.IN_FLIGHT)
              and event.nextAttemptAt <= :now
            order by event.nextAttemptAt asc
            """)
    List<UUID> findDispatchable(@Param("now") OffsetDateTime now, Pageable pageable);

    // status stops a resend and nextAttemptAt stops two holders; claiming spends the attempt.
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

    @Query("""
            select event from OutboxEvent event
            where event.status = com.auvan.api.outbox.entity.OutboxStatus.DEAD
            order by event.processedAt desc, event.createdAt desc
            """)
    List<OutboxEvent> findDead(Pageable pageable);

    // Checked first: hitting the unique constraint would make the approval rollback-only.
    boolean existsByDedupeKey(String dedupeKey);

    // Deleted rather than killed, so a rescheduled reminder's dedupe key is free to be scheduled again.
    @Transactional
    @Modifying
    @Query("""
            delete from OutboxEvent event
            where event.aggregateId = :aggregateId
              and event.dedupeKey is not null
              and event.status = com.auvan.api.outbox.entity.OutboxStatus.PENDING
            """)
    int deleteScheduled(@Param("aggregateId") UUID aggregateId);

    // Spares state-change rows and IN_FLIGHT sends; no due-time clause, so due reminders go too.
    @Transactional
    @Modifying
    @Query("""
            update OutboxEvent event
            set event.status = com.auvan.api.outbox.entity.OutboxStatus.DEAD,
                event.processedAt = :now,
                event.lastError = :reason
            where event.aggregateId = :aggregateId
              and event.dedupeKey is not null
              and event.status = com.auvan.api.outbox.entity.OutboxStatus.PENDING
            """)
    int cancelScheduled(@Param("aggregateId") UUID aggregateId, @Param("now") OffsetDateTime now,
                        @Param("reason") String reason);
}
