package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.BookingCooldownClear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BookingCooldownClearRepository extends JpaRepository<BookingCooldownClear, UUID> {
    @Query("select max(clear.clearedAt) from BookingCooldownClear clear where clear.userId = :userId")
    Optional<OffsetDateTime> findLatestClearedAt(@Param("userId") UUID userId);
}
