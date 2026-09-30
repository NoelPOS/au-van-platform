package com.auvan.api.inventory.entity;

import jakarta.persistence.Column;
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
@Table(name = "trip_seats", uniqueConstraints = {
        @UniqueConstraint(name = "trip_seats_label_unique", columnNames = {"trip_id", "label"}),
        @UniqueConstraint(name = "trip_seats_position_unique",
                columnNames = {"trip_id", "row_number", "column_number"})
})
public class TripSeat {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false, length = 32)
    private String label;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "column_number", nullable = false)
    private int columnNumber;

    protected TripSeat() { }

    public TripSeat(String label, int rowNumber, int columnNumber, Trip trip) {
        this.label = label;
        this.rowNumber = rowNumber;
        this.columnNumber = columnNumber;
        this.trip = trip;
    }

    public UUID getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getLabel() { return label; }
    public int getRowNumber() { return rowNumber; }
    public int getColumnNumber() { return columnNumber; }
}
