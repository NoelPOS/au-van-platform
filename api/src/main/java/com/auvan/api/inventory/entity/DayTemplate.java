package com.auvan.api.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "day_templates")
public class DayTemplate {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("departureTime ASC")
    private List<DayTemplateDeparture> departures = new ArrayList<>();

    protected DayTemplate() { }

    public DayTemplate(String name, List<DayTemplateDeparture> departures) {
        replace(name, departures);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public List<DayTemplateDeparture> getDepartures() { return List.copyOf(departures); }

    public void replace(String name, List<DayTemplateDeparture> departures) {
        this.name = name;
        this.departures.clear();
        departures.forEach(departure -> {
            departure.assignTo(this);
            this.departures.add(departure);
        });
    }
}
