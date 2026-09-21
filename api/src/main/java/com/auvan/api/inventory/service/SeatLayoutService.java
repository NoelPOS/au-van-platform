package com.auvan.api.inventory.service;

import com.auvan.api.inventory.dto.CreateSeatLayoutRequest;
import com.auvan.api.inventory.dto.SeatInput;
import com.auvan.api.inventory.dto.SeatLayoutResponse;
import com.auvan.api.inventory.dto.UpdateSeatLayoutRequest;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
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

    public SeatLayoutService(SeatLayoutRepository seatLayouts) {
        this.seatLayouts = seatLayouts;
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
        // seat_layout_seats_label_unique is checked row by row, and Hibernate
        // orders inserts ahead of deletes within one flush, so a replacement
        // seat reusing a label would collide with the row it replaces. Flushing
        // the removal on its own sends the deletes first, for the same reason
        // the seat hold path deletes reclaimed claims in bulk before inserting.
        seatLayouts.flush();
        seatLayout.addSeats(replacements);
        return SeatLayoutResponse.from(seatLayout);
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
