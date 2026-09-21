package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
