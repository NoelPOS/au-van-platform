package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Scoped by owner rather than filtered afterwards, so another student's
     * booking is indistinguishable from one that does not exist.
     */
    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The same owner-scoped lookup, with the booking's row locked until the
     * transaction ends. Scoped by owner for the same reason and in the same
     * statement, so a non-owner matches nothing and locks nothing.
     *
     * <p>This is what serialises a read-then-write on {@code status}, which no
     * constraint can see: two payment-proof submissions both {@code UPDATE} a
     * row that already exists, so neither violates anything and one booking
     * ends up with two proofs. Every decision after this call must be made from
     * the booking it returned — a reading taken before the lock is stale and
     * defeats it, the same rule {@link SeatClaimRepository#lockByHoldId} states.
     *
     * <p>No fetch join: PostgreSQL refuses {@code FOR UPDATE} on the nullable
     * side of an outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id and booking.userId = :userId")
    Optional<Booking> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
