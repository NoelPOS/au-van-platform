package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.WaitlistEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {
    Optional<WaitlistEntry> findByTripIdAndUserId(UUID tripId, UUID userId);

    // Lock before any read of the entry: an earlier read is handed back stale after the lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from WaitlistEntry entry where entry.id = :id and entry.userId = :userId")
    Optional<WaitlistEntry> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select entry from WaitlistEntry entry
            join fetch entry.trip
            where entry.userId = :userId
              and entry.status in (com.auvan.api.booking.entity.WaitlistStatus.WAITING,
                                   com.auvan.api.booking.entity.WaitlistStatus.PROMOTED)
            order by entry.joinedAt asc, entry.id asc
            """)
    List<WaitlistEntry> findQueuedByUserId(@Param("userId") UUID userId);

    // id breaks ties so the order is deterministic.
    @Query("""
            select entry from WaitlistEntry entry
            where entry.trip.id = :tripId
            order by entry.joinedAt asc, entry.id asc
            """)
    List<WaitlistEntry> findByTripIdOrderByJoinedAt(@Param("tripId") UUID tripId);

    // No fetch join: PostgreSQL refuses FOR UPDATE on the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from WaitlistEntry entry where entry.id = :id")
    Optional<WaitlistEntry> lockById(@Param("id") UUID id);

    // Ids, not entities, here and below: an entity loaded before lockById is handed back stale.
    @Query("""
            select entry.id from WaitlistEntry entry
            where entry.trip.id = :tripId
              and entry.status in (com.auvan.api.booking.entity.WaitlistStatus.WAITING,
                                   com.auvan.api.booking.entity.WaitlistStatus.PROMOTED)
            order by entry.joinedAt asc, entry.id asc
            """)
    List<UUID> findQueuedIdsByTripId(@Param("tripId") UUID tripId);

    @Query("""
            select distinct entry.trip.id from WaitlistEntry entry
            where entry.status = com.auvan.api.booking.entity.WaitlistStatus.WAITING
              and entry.trip.status = com.auvan.api.inventory.entity.TripStatus.ACTIVE
              and entry.trip.departureAt > :now
            """)
    List<UUID> findPromotableTripIds(@Param("now") OffsetDateTime now, Pageable pageable);

    @Query("""
            select entry.id from WaitlistEntry entry
            where entry.trip.id = :tripId
              and entry.status = com.auvan.api.booking.entity.WaitlistStatus.WAITING
            order by entry.joinedAt asc, entry.id asc
            """)
    List<UUID> findNextWaiting(@Param("tripId") UUID tripId, Pageable pageable);

    @Query("""
            select entry.id from WaitlistEntry entry
            where entry.status = com.auvan.api.booking.entity.WaitlistStatus.PROMOTED
              and entry.promotionExpiresAt <= :now
            order by entry.promotionExpiresAt asc
            """)
    List<UUID> findLapsedPromotionIds(@Param("now") OffsetDateTime now, Pageable pageable);
}
