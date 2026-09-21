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
@Table(name = "seat_layouts")
public class SeatLayout {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @OneToMany(mappedBy = "seatLayout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowNumber ASC, columnNumber ASC")
    private List<SeatLayoutSeat> seats = new ArrayList<>();

    protected SeatLayout() { }

    public SeatLayout(String name, List<SeatLayoutSeat> seats) {
        this.name = name;
        addSeats(seats);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public List<SeatLayoutSeat> getSeats() { return List.copyOf(seats); }

    public void rename(String name) {
        this.name = name;
    }

    /**
     * Orphan-removes every seat. Replacing a layout's seats is two steps rather
     * than one because the removal has to reach the database before the
     * replacements are added; see {@code SeatLayoutService.update}.
     */
    public void removeSeats() {
        this.seats.clear();
    }

    public void addSeats(List<SeatLayoutSeat> seats) {
        seats.forEach(seat -> {
            seat.assignTo(this);
            this.seats.add(seat);
        });
    }
}
