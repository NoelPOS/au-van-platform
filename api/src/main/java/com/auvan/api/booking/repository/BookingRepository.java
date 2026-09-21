package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.Booking;
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

    /**
     * The same lock as {@link #lockByIdAndUserId}, without the owner scope,
     * for the administrator reviewing a payment proof: staff are not the
     * booking's owner, so the owner-scoped twin would match nothing and answer
     * as if the booking did not exist. Everything its javadoc says about
     * deciding only from the booking it returned applies here unchanged.
     *
     * <p>Only ever call this from a path that has already authorized the
     * caller; it is deliberately blind to who is asking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> lockById(@Param("id") UUID id);

    /**
     * The ids of bookings whose payment deadline has passed while they were
     * still waiting for one, oldest deadline first.
     *
     * <p><strong>Ids, not entities.</strong> Loading the bookings here would put
     * them in the persistence context before {@link #lockById}, and every later
     * read — including the one taken after the lock — would hand that pre-lock
     * instance straight back. A booking an administrator confirmed in between
     * would still read {@code PAYMENT_UNDER_REVIEW}, the sweep would cancel it
     * and delete the claims of a paid seat, and the guard would go on looking
     * exactly like a guard while every test passed. This is the trap
     * {@link PaymentProofRepository#findBookingIdById} records in full, and
     * returning {@code List<Booking>} from here is the single most likely way to
     * ship a broken sweep with a green suite.
     *
     * <p>A row this names may stop being expirable a moment later, so nothing is
     * decided from this list: it is a candidate hint, and the lock decides.
     */
    @Query("""
            select booking.id from Booking booking
            where booking.status in (com.auvan.api.booking.entity.BookingStatus.PENDING_PAYMENT,
                                     com.auvan.api.booking.entity.BookingStatus.PAYMENT_UNDER_REVIEW,
                                     com.auvan.api.booking.entity.BookingStatus.PAYMENT_REJECTED)
              and booking.paymentDeadlineAt <= :now
            order by booking.paymentDeadlineAt asc
            """)
    List<UUID> findExpirable(@Param("now") OffsetDateTime now, Pageable pageable);
}
