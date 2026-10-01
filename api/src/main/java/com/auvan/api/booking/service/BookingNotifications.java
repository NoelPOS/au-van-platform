package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.notification.dto.BookingNotification;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class BookingNotifications {
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Bangkok"));

    private BookingNotifications() { }

    static String moment(OffsetDateTime value) {
        return MOMENT.format(value);
    }

    static BookingNotification of(Booking booking, String detail) {
        Trip trip = booking.getTrip();
        return new BookingNotification(booking.getReference(), detail, trip.getRoute().getOrigin(),
                trip.getRoute().getDestination(), trip.getDepartureAt(),
                booking.getSeats().stream().map(BookingSeat::getTripSeat).map(TripSeat::getLabel).sorted().toList(),
                booking.getTotalFare(), booking.getPaymentDeadlineAt());
    }
}
