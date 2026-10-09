package com.auvan.api.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trips", uniqueConstraints =
        @UniqueConstraint(name = "trips_vehicle_departure_unique",
                columnNames = {"vehicle_id", "departure_at"}))
public class Trip {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private VanRoute route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "departure_at", nullable = false)
    private OffsetDateTime departureAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TripStatus status;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowNumber ASC, columnNumber ASC")
    private List<TripSeat> seats = new ArrayList<>();

    protected Trip() { }

    public Trip(VanRoute route, Vehicle vehicle, OffsetDateTime departureAt) {
        this.route = route;
        this.vehicle = vehicle;
        this.departureAt = departureAt;
        this.fare = route.getFare();
        this.durationMinutes = route.getDurationMinutes();
        this.status = TripStatus.ACTIVE;
        vehicle.getSeatLayout().getSeats().forEach(seat -> seats.add(new TripSeat(
                seat.getLabel(), seat.getRowNumber(), seat.getColumnNumber(), this)));
    }

    public UUID getId() { return id; }
    public VanRoute getRoute() { return route; }
    public Vehicle getVehicle() { return vehicle; }
    public OffsetDateTime getDepartureAt() { return departureAt; }
    public BigDecimal getFare() { return fare; }
    public int getDurationMinutes() { return durationMinutes; }
    public TripStatus getStatus() { return status; }
    public String getCancellationReason() { return cancellationReason; }
    public List<TripSeat> getSeats() { return List.copyOf(seats); }

    public void reschedule(OffsetDateTime departureAt) {
        this.departureAt = departureAt;
    }

    public boolean isCancelled() {
        return status == TripStatus.CANCELLED;
    }

    public boolean hasDepartedAt(OffsetDateTime moment) {
        return !departureAt.isAfter(moment);
    }

    public void cancel(String reason) {
        this.status = TripStatus.CANCELLED;
        this.cancellationReason = reason;
    }
}
