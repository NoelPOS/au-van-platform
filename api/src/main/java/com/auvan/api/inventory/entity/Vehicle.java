package com.auvan.api.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_layout_id", nullable = false)
    private SeatLayout seatLayout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VehicleStatus status;

    protected Vehicle() { }

    public Vehicle(String code, String name, SeatLayout seatLayout) {
        this.code = code;
        this.name = name;
        this.seatLayout = seatLayout;
        this.status = VehicleStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public SeatLayout getSeatLayout() { return seatLayout; }
    public VehicleStatus getStatus() { return status; }

    public void update(String code, String name, SeatLayout seatLayout, VehicleStatus status) {
        this.code = code;
        this.name = name;
        this.seatLayout = seatLayout;
        this.status = status;
    }
}
