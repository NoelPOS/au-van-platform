package com.auvan.api.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "day_template_departures")
public class DayTemplateDeparture {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_template_id", nullable = false)
    private DayTemplate template;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private VanRoute route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    protected DayTemplateDeparture() { }

    public DayTemplateDeparture(LocalTime departureTime, VanRoute route, Vehicle vehicle) {
        this.departureTime = departureTime;
        this.route = route;
        this.vehicle = vehicle;
    }

    void assignTo(DayTemplate template) { this.template = template; }
    public LocalTime getDepartureTime() { return departureTime; }
    public VanRoute getRoute() { return route; }
    public Vehicle getVehicle() { return vehicle; }
}
