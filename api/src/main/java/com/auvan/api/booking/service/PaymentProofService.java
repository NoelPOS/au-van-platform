package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.config.PaymentProofProperties;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentProofService {
    private final BookingRepository bookings;
    private final PaymentProofRepository proofs;
    private final PaymentProofStorage storage;
    private final OutboxRecorder outbox;
    private final BookingProperties bookingProperties;
    private final long maxFileBytes;

    public PaymentProofService(BookingRepository bookings, PaymentProofRepository proofs,
                               PaymentProofStorage storage, OutboxRecorder outbox,
                               BookingProperties bookingProperties, PaymentProofProperties properties) {
        this.bookings = bookings;
        this.proofs = proofs;
        this.storage = storage;
        this.outbox = outbox;
        this.bookingProperties = bookingProperties;
        this.maxFileBytes = properties.maxFileSize().toBytes();
    }

    @Transactional
    public BookingResponse submit(UUID userId, UUID bookingId, MultipartFile file) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = bookings.lockByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> Problems.notFound("booking_not_found", "Booking not found."));
        if (!booking.isAwaitingPaymentProof()) {
            throw Problems.conflict("booking_not_awaiting_payment",
                    "This booking is not waiting for a payment proof.");
        }

        PaymentProofFile.AcceptedImage image = PaymentProofFile.accept(file.getContentType());
        PaymentProofFile.assertSizeWithin(file.getSize(), maxFileBytes);

        String objectKey = PaymentProofFile.objectKey(bookingId, image.extension(), now);
        // Store the image before any row is written, so a storage failure leaves nothing behind.
        store(objectKey, image.contentType(), read(file));

        proofs.save(new PaymentProof(booking, userId, objectKey, image.contentType(), file.getSize(), now));
        String detail = "Payment proof submitted for review.";
        booking.recordEvent(BookingEventType.PAYMENT_PROOF_SUBMITTED, detail, userId, now);
        booking.markPaymentUnderReview(
                bookingProperties.departureBoundFor(booking.getTrip().getDepartureAt()), now);
        outbox.record(OutboxEventType.PAYMENT_PROOF_SUBMITTED, booking.getId(), booking.getUserId(),
                new BookingNotification(booking.getReference(), detail), now);
        return BookingResponse.from(booking);
    }

    private void store(String objectKey, String contentType, byte[] content) {
        try {
            storage.store(objectKey, contentType, content);
        } catch (RuntimeException failure) {
            throw Problems.serviceUnavailable("payment_proof_storage_unavailable",
                    "The payment proof could not be stored. Please try again.", failure);
        }
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException failure) {
            throw Problems.serviceUnavailable("payment_proof_unreadable",
                    "The payment proof could not be read. Please try again.", failure);
        }
    }
}
