package com.auvan.api.inventory.service;

import com.auvan.api.inventory.dto.CreateSeatLayoutRequest;
import com.auvan.api.inventory.dto.SeatInput;
import com.auvan.api.inventory.dto.SeatLayoutResponse;
import com.auvan.api.inventory.dto.UpdateSeatLayoutRequest;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SeatLayoutService {
    private final SeatLayoutRepository seatLayouts;
    private final VehicleRepository vehicles;

    public SeatLayoutService(SeatLayoutRepository seatLayouts, VehicleRepository vehicles) {
        this.seatLayouts = seatLayouts;
        this.vehicles = vehicles;
    }

    @Transactional(readOnly = true)
    public List<SeatLayoutResponse> list() {
        return seatLayouts.findAll().stream().map(SeatLayoutResponse::from).toList();
    }

    @Transactional
    public SeatLayoutResponse create(CreateSeatLayoutRequest request) {
        String name = request.name().trim();
        if (seatLayouts.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A seat layout with that name already exists.");
        }
        return SeatLayoutResponse.from(seatLayouts.save(new SeatLayout(name, toSeats(request.seats()))));
    }

    @Transactional
    public SeatLayoutResponse update(UUID id, UpdateSeatLayoutRequest request) {
        SeatLayout seatLayout = find(id);
        String name = request.name().trim();
        if (seatLayouts.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A seat layout with that name already exists.");
        }
        List<SeatLayoutSeat> replacements = toSeats(request.seats());
        seatLayout.rename(name);
        seatLayout.removeSeats();
        // Flush the removal first: Hibernate inserts before deleting, so a reused label collides.
        seatLayouts.flush();
        seatLayout.addSeats(replacements);
        return SeatLayoutResponse.from(seatLayout);
    }

    @Transactional
    public void delete(UUID id) {
        SeatLayout seatLayout = find(id);
        List<String> vanCodes = vehicles.findBySeatLayoutIdOrderByCode(id).stream().map(Vehicle::getCode).toList();
        if (!vanCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Used by " + String.join(", ", vanCodes) + ". Move them to another layout first.");
        }
        seatLayouts.delete(seatLayout);
    }

    private SeatLayout find(UUID id) {
        return seatLayouts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat layout not found."));
    }

    private List<SeatLayoutSeat> toSeats(List<SeatInput> input) {
        Set<String> labels = new HashSet<>();
        Set<String> positions = new HashSet<>();
        for (SeatInput seat : input) {
            if (!labels.add(seat.label().trim().toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat labels must be unique.");
            }
            if (!positions.add(seat.rowNumber() + ":" + seat.columnNumber())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat positions must be unique.");
            }
        }
        return input.stream()
                .map(seat -> new SeatLayoutSeat(seat.label().trim(), seat.rowNumber(), seat.columnNumber()))
                .toList();
    }
}
