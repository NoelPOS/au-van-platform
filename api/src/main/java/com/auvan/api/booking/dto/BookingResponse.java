package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEvent;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.inventory.entity.Trip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String reference,
        BookingStatus status,
        BookedTrip trip,
        String passengerName,
        String passengerPhone,
        BigDecimal totalFare,
        List<BookedSeat> seats,
        List<BookingEventResponse> events,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime paymentDeadlineAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getReference(),
                booking.getStatus(),
                BookedTrip.from(booking.getTrip()),
                booking.getPassengerName(),
                booking.getPassengerPhone(),
                booking.getTotalFare(),
                booking.getSeats().stream().map(BookedSeat::from).toList(),
                booking.getEvents().stream().map(BookingEventResponse::from).toList(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getPaymentDeadlineAt());
    }

    public record BookedTrip(UUID id, String origin, String destination, OffsetDateTime departureAt) {
        static BookedTrip from(Trip trip) {
            return new BookedTrip(trip.getId(), trip.getRoute().getOrigin(), trip.getRoute().getDestination(),
                    trip.getDepartureAt());
        }
    }

    public record BookedSeat(UUID seatId, String label) {
        static BookedSeat from(BookingSeat seat) {
            return new BookedSeat(seat.getTripSeat().getId(), seat.getTripSeat().getLabel());
        }
    }

    public record BookingEventResponse(BookingEventType type, String detail, UUID actorUserId,
                                       OffsetDateTime createdAt) {
        static BookingEventResponse from(BookingEvent event) {
            return new BookingEventResponse(event.getEventType(), event.getDetail(), event.getActorUserId(),
                    event.getCreatedAt());
        }
    }
}
