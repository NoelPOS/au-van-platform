package com.auvan.api.booking.service;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.RefundStatus;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RefundService {
    private final BookingRepository bookings;

    public RefundService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> due() {
        return bookings.findByRefundStatusOrderByUpdatedAtAsc(RefundStatus.DUE).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional
    public BookingResponse markRefunded(UUID adminId, UUID bookingId, String note) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = bookings.lockById(bookingId)
                .orElseThrow(() -> Problems.notFound("booking_not_found", "Booking not found."));
        if (booking.getRefundStatus() == RefundStatus.REFUNDED) {
            throw Problems.conflict("refund_already_recorded", "That refund has already been recorded.");
        }
        if (!booking.isRefundDue()) {
            throw Problems.conflict("refund_not_due", "No refund is due on that booking.");
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        booking.markRefunded(adminId, trimmed, now);
        String detail = "Refunded " + BookingNotifications.baht(booking.getTotalFare()) + "."
                + (trimmed == null ? "" : " " + trimmed);
        booking.recordEvent(BookingEventType.REFUNDED, detail, adminId, now);
        return BookingResponse.from(booking);
    }
}
