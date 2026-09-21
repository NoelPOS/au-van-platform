package com.auvan.api.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class VanRoute {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 100)
    private String origin;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RouteStatus status;

    protected VanRoute() { }

    public VanRoute(String origin, String destination, BigDecimal fare, int durationMinutes) {
        this.origin = origin;
        this.destination = destination;
        this.fare = fare;
        this.durationMinutes = durationMinutes;
        this.status = RouteStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public BigDecimal getFare() { return fare; }
    public int getDurationMinutes() { return durationMinutes; }
    public RouteStatus getStatus() { return status; }

    public void update(String origin, String destination, BigDecimal fare, int durationMinutes, RouteStatus status) {
        this.origin = origin;
        this.destination = destination;
        this.fare = fare;
        this.durationMinutes = durationMinutes;
        this.status = status;
    }
}
