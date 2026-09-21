package com.auvan.api.booking.service;

import com.auvan.api.booking.config.PaymentProofProperties;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A student's payment-proof submission: the image goes to object storage, the
 * row and the booking's new status go to PostgreSQL, and the booking's history
 * records who submitted it.
 *
 * <p>The image is written <em>before</em> any row, and the whole method is one
 * transaction. Storage failing therefore leaves nothing behind at all — no
 * proof row, no status change, no history entry — which is what the issue asks
 * for. The opposite order would risk the failure the admin review in #52 has no
 * answer for: a proof row whose image was never stored.
 */
@Service
public class PaymentProofService {
    private final BookingRepository bookings;
    private final PaymentProofRepository proofs;
    private final PaymentProofStorage storage;
    private final long maxFileBytes;

    public PaymentProofService(BookingRepository bookings, PaymentProofRepository proofs,
                               PaymentProofStorage storage, PaymentProofProperties properties) {
        this.bookings = bookings;
        this.proofs = proofs;
        this.storage = storage;
        this.maxFileBytes = properties.maxFileSize().toBytes();
    }

    @Transactional
    public BookingResponse submit(UUID userId, UUID bookingId, MultipartFile file) {
        OffsetDateTime now = OffsetDateTime.now();
        // Another student's booking answers exactly as one that does not exist,
        // the same way BookingService.load does.
        Booking booking = bookings.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> Problems.notFound("booking_not_found", "Booking not found."));
        if (!booking.isAwaitingPaymentProof()) {
            throw Problems.conflict("booking_not_awaiting_payment",
                    "This booking is not waiting for a payment proof.");
        }

        PaymentProofFile.AcceptedImage image = PaymentProofFile.accept(file.getContentType());
        PaymentProofFile.assertSizeWithin(file.getSize(), maxFileBytes);

        String objectKey = PaymentProofFile.objectKey(bookingId, image.extension(), now);
        store(objectKey, image.contentType(), read(file));

        proofs.save(new PaymentProof(booking, userId, objectKey, image.contentType(), file.getSize(), now));
        booking.recordEvent(BookingEventType.PAYMENT_PROOF_SUBMITTED, "Payment proof submitted for review.",
                userId, now);
        booking.markPaymentUnderReview(now);
        return BookingResponse.from(booking);
    }

    private void store(String objectKey, String contentType, byte[] content) {
        try {
            storage.store(objectKey, contentType, content);
        } catch (RuntimeException failure) {
            // Without this the SDK's own exception escapes as a bare 500 whose
            // body names the bucket and the endpoint.
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
