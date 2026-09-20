package com.auvan.api.inventory.repository;

import com.auvan.api.inventory.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    boolean existsByVehicleIdAndDepartureAt(UUID vehicleId, OffsetDateTime departureAt);
    boolean existsByVehicleIdAndDepartureAtAndIdNot(UUID vehicleId, OffsetDateTime departureAt, UUID id);
}
