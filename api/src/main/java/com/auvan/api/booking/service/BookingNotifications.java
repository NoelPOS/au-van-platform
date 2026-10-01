package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.notification.dto.BookingNotification;

final class BookingNotifications {
    private BookingNotifications() { }

    static BookingNotification of(Booking booking, String detail) {
        Trip trip = booking.getTrip();
        return new BookingNotification(booking.getReference(), detail, trip.getRoute().getOrigin(),
                trip.getRoute().getDestination(), trip.getDepartureAt(),
                booking.getSeats().stream().map(BookingSeat::getTripSeat).map(TripSeat::getLabel).sorted().toList(),
                booking.getTotalFare(), booking.getPaymentDeadlineAt());
    }
}
