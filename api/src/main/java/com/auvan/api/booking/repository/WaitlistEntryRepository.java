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
    /**
     * The caller's own entry on a trip, whatever state it is in. Joining is
     * idempotent through this lookup: a student who is already queued gets the
     * entry they already have, with the place they already had.
     */
    Optional<WaitlistEntry> findByTripIdAndUserId(UUID tripId, UUID userId);

    /**
     * Scoped by owner rather than filtered afterwards, so another student's
     * entry is indistinguishable from one that does not exist — the same rule
     * {@link BookingRepository#findByIdAndUserId} states, and what makes
     * leaving someone else's entry answer exactly as leaving an imaginary one.
     */
    Optional<WaitlistEntry> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The caller's queued entries across every trip, so the student surface
     * costs one request rather than one per trip in the list.
     */
    @Query("""
            select entry from WaitlistEntry entry
            join fetch entry.trip
            where entry.userId = :userId
              and entry.status in (com.auvan.api.booking.entity.WaitlistStatus.WAITING,
                                   com.auvan.api.booking.entity.WaitlistStatus.PROMOTED)
            order by entry.joinedAt asc, entry.id asc
            """)
    List<WaitlistEntry> findQueuedByUserId(@Param("userId") UUID userId);

    /**
     * A trip's whole queue in join order, terminal entries included, which is
     * what a position is derived from and what the administrator's view reads.
     *
     * <p>{@code id} breaks the tie. Two students who joined in the same
     * millisecond would otherwise be ordered differently on different reads,
     * and "deterministic" in the acceptance criterion means predictable rather
     * than merely repeatable.
     */
    @Query("""
            select entry from WaitlistEntry entry
            where entry.trip.id = :tripId
            order by entry.joinedAt asc, entry.id asc
            """)
    List<WaitlistEntry> findByTripIdOrderByJoinedAt(@Param("tripId") UUID tripId);

    /**
     * The entry with its row locked until the transaction ends.
     *
     * <p>This is what the promotion sweep (#69) opens with, and every decision
     * it makes must come from what this returned. Nothing a candidate query
     * said is still guaranteed true: the student may have left, or another
     * sweeper may have promoted them, since the candidate list was read. The
     * discipline is ADR-008's, restated by ADR-009, ADR-010 and ADR-011, and
     * {@link BookingRepository#lockById} is the same finder for a booking.
     *
     * <p>No fetch join: PostgreSQL refuses {@code FOR UPDATE} on the nullable
     * side of an outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from WaitlistEntry entry where entry.id = :id")
    Optional<WaitlistEntry> lockById(@Param("id") UUID id);

    /**
     * The ids of trips that have somebody waiting and are still worth promoting
     * onto — active, and not yet departed.
     *
     * <p><strong>Ids, not entities</strong>, here and in the two finders below,
     * for the reason {@link BookingRepository#findExpirable} sets out at length:
     * entities loaded before {@link #lockById} are handed straight back from the
     * persistence context, so the read behind the lock returns the pre-lock
     * instance and the guard goes on looking exactly like a guard while the
     * suite stays green.
     */
    @Query("""
            select distinct entry.trip.id from WaitlistEntry entry
            where entry.status = com.auvan.api.booking.entity.WaitlistStatus.WAITING
              and entry.trip.status = com.auvan.api.inventory.entity.TripStatus.ACTIVE
              and entry.trip.departureAt > :now
            """)
    List<UUID> findPromotableTripIds(@Param("now") OffsetDateTime now, Pageable pageable);

    /**
     * The ids of the students next in line on one trip, in join order. A
     * candidate hint only; {@link #lockById} decides.
     */
    @Query("""
            select entry.id from WaitlistEntry entry
            where entry.trip.id = :tripId
              and entry.status = com.auvan.api.booking.entity.WaitlistStatus.WAITING
            order by entry.joinedAt asc, entry.id asc
            """)
    List<UUID> findNextWaiting(@Param("tripId") UUID tripId, Pageable pageable);

    /**
     * The ids of promotions whose window has run out, oldest first: the student
     * was offered a seat and did nothing, so the entry ends and the seat goes to
     * the next in line (ADR-011). This is exactly
     * {@code waitlist_entries_promotion_idx}'s predicate.
     */
    @Query("""
            select entry.id from WaitlistEntry entry
            where entry.status = com.auvan.api.booking.entity.WaitlistStatus.PROMOTED
              and entry.promotionExpiresAt <= :now
            order by entry.promotionExpiresAt asc
            """)
    List<UUID> findLapsedPromotionIds(@Param("now") OffsetDateTime now, Pageable pageable);
}
