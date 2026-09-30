package com.auvan.api.booking.entity;

import com.auvan.api.inventory.entity.TripSeat;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "booking_seats", uniqueConstraints =
        @UniqueConstraint(name = "booking_seats_seat_unique", columnNames = {"booking_id", "trip_seat_id"}))
public class BookingSeat {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_seat_id", nullable = false)
    private TripSeat tripSeat;

    protected BookingSeat() { }

    BookingSeat(Booking booking, TripSeat tripSeat) {
        this.booking = booking;
        this.tripSeat = tripSeat;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public TripSeat getTripSeat() { return tripSeat; }
}
