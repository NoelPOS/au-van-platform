package com.auvan.api.inventory.repository;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    boolean existsByVehicleIdAndDepartureAt(UUID vehicleId, OffsetDateTime departureAt);
    boolean existsByVehicleIdAndDepartureAtAndIdNot(UUID vehicleId, OffsetDateTime departureAt, UUID id);

    // No fetch join: PostgreSQL refuses FOR UPDATE on the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trip from Trip trip where trip.id = :id")
    Optional<Trip> lockById(@Param("id") UUID id);

    @Query("""
            select trip from Trip trip
            join fetch trip.route
            left join fetch trip.seats
            where trip.status = :status and trip.departureAt > :departingAfter
            order by trip.departureAt
            """)
    List<Trip> findBookable(@Param("status") TripStatus status, @Param("departingAfter") OffsetDateTime departingAfter);
}
