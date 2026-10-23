package com.auvan.api.inventory.service;

import com.auvan.api.inventory.dto.CreateVehicleRequest;
import com.auvan.api.inventory.dto.UpdateVehicleRequest;
import com.auvan.api.inventory.dto.VehicleResponse;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.entity.VehicleStatus;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleService {
    private final VehicleRepository vehicles;
    private final SeatLayoutRepository seatLayouts;
    private final TripRepository trips;

    public VehicleService(VehicleRepository vehicles, SeatLayoutRepository seatLayouts, TripRepository trips) {
        this.vehicles = vehicles;
        this.seatLayouts = seatLayouts;
        this.trips = trips;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list() {
        return vehicles.findAll().stream().map(VehicleResponse::from).toList();
    }

    @Transactional
    public VehicleResponse create(CreateVehicleRequest request) {
        String code = request.code().trim();
        if (vehicles.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A vehicle with that code already exists.");
        }
        return VehicleResponse.from(vehicles.save(new Vehicle(code, request.name().trim(), findSeatLayout(request.seatLayoutId()))));
    }

    @Transactional
    public VehicleResponse update(UUID id, UpdateVehicleRequest request) {
        Vehicle vehicle = find(id);
        String code = request.code().trim();
        if (vehicles.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A vehicle with that code already exists.");
        }
        if (vehicle.getStatus() == VehicleStatus.ACTIVE && request.status() == VehicleStatus.INACTIVE) {
            assertNoUpcomingTrips(vehicle);
        }
        vehicle.update(code, request.name().trim(), findSeatLayout(request.seatLayoutId()), request.status());
        return VehicleResponse.from(vehicle);
    }

    private void assertNoUpcomingTrips(Vehicle vehicle) {
        long upcoming = trips.countByVehicleIdAndStatusAndDepartureAtAfter(
                vehicle.getId(), TripStatus.ACTIVE, OffsetDateTime.now());
        if (upcoming > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, vehicle.getCode() + " still has " + upcoming
                    + (upcoming == 1 ? " upcoming trip" : " upcoming trips") + ". Cancel them before marking it inactive.");
        }
    }

    private Vehicle find(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found."));
    }

    private SeatLayout findSeatLayout(UUID id) {
        return seatLayouts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat layout not found."));
    }
}
