package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;

import java.util.List;
import java.util.UUID;

public record SeatLayoutResponse(UUID id, String name, List<SeatResponse> seats) {
    public static SeatLayoutResponse from(SeatLayout layout) {
        return new SeatLayoutResponse(layout.getId(), layout.getName(), layout.getSeats().stream()
                .map(SeatResponse::from)
                .toList());
    }

    public record SeatResponse(String label, int rowNumber, int columnNumber) {
        static SeatResponse from(SeatLayoutSeat seat) {
            return new SeatResponse(seat.getLabel(), seat.getRowNumber(), seat.getColumnNumber());
        }
    }
}
