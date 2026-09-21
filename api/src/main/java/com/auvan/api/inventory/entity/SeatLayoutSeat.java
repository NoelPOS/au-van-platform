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
// Mirrored from V2 under the same names, so the mapping and the migration
// cannot describe this table differently without it showing in review.
@Table(name = "seat_layout_seats", uniqueConstraints = {
        @UniqueConstraint(name = "seat_layout_seats_label_unique",
                columnNames = {"seat_layout_id", "label"}),
        @UniqueConstraint(name = "seat_layout_seats_position_unique",
                columnNames = {"seat_layout_id", "row_number", "column_number"})
})
public class SeatLayoutSeat {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_layout_id", nullable = false)
    private SeatLayout seatLayout;

    @Column(nullable = false, length = 32)
    private String label;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "column_number", nullable = false)
    private int columnNumber;

    protected SeatLayoutSeat() { }

    public SeatLayoutSeat(String label, int rowNumber, int columnNumber) {
        this.label = label;
        this.rowNumber = rowNumber;
        this.columnNumber = columnNumber;
    }

    void assignTo(SeatLayout seatLayout) { this.seatLayout = seatLayout; }
    public String getLabel() { return label; }
    public int getRowNumber() { return rowNumber; }
    public int getColumnNumber() { return columnNumber; }
}
