package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    List<Booking> findByTripId(UUID tripId);

    List<Booking> findByRefundStatusOrderByUpdatedAtAsc(RefundStatus refundStatus);

    Optional<Booking> findFirstByUserIdAndStatusInOrderByCreatedAtAsc(UUID userId, Collection<BookingStatus> statuses);

    boolean existsByUserIdAndTripIdAndStatusNot(UUID userId, UUID tripId, BookingStatus status);

    @Query("""
            select event.createdAt from BookingEvent event
            where event.booking.userId = :userId
              and event.eventType = com.auvan.api.booking.entity.BookingEventType.EXPIRED
              and event.createdAt > :since
            order by event.createdAt desc
            """)
    List<OffsetDateTime> findExpiryTimesSince(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    // No fetch join: PostgreSQL refuses FOR UPDATE on the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id and booking.userId = :userId")
    Optional<Booking> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // Not owner-scoped: call only after the caller has been authorised.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> lockById(@Param("id") UUID id);

    // Ids, not entities: a booking loaded here is handed back stale by the later lockById,
    // so the lock would decide nothing.
    @Query("""
            select booking.id from Booking booking
            where booking.status in (com.auvan.api.booking.entity.BookingStatus.PENDING_PAYMENT,
                                     com.auvan.api.booking.entity.BookingStatus.PAYMENT_REJECTED)
              and booking.paymentDeadlineAt <= :now
            order by booking.paymentDeadlineAt asc
            """)
    List<UUID> findExpirable(@Param("now") OffsetDateTime now, Pageable pageable);
}
