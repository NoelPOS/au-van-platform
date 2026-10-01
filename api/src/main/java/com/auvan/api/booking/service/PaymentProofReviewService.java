package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.PaymentProofResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentProofReviewService {
    private static final int MAX_NOTE_LENGTH = 500;

    private final BookingRepository bookings;
    private final PaymentProofRepository proofs;
    private final PaymentProofStorage storage;
    private final OutboxRecorder outbox;
    private final DepartureReminderService reminders;
    private final BookingProperties properties;

    public PaymentProofReviewService(BookingRepository bookings, PaymentProofRepository proofs,
                                     PaymentProofStorage storage, OutboxRecorder outbox,
                                     DepartureReminderService reminders, BookingProperties properties) {
        this.bookings = bookings;
        this.proofs = proofs;
        this.storage = storage;
        this.outbox = outbox;
        this.reminders = reminders;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<PaymentProofResponse> list() {
        return proofs.findByStatusOrderByCreatedAt(PaymentProofStatus.SUBMITTED).stream()
                .map(PaymentProofResponse::from)
                .toList();
    }

    public record ProofImage(String contentType, byte[] content) { }

    @Transactional(readOnly = true)
    public ProofImage image(UUID proofId) {
        PaymentProof proof = proofs.findById(proofId).orElseThrow(PaymentProofReviewService::proofNotFound);
        return new ProofImage(proof.getContentType(), load(proof.getObjectKey()));
    }

    @Transactional
    public BookingResponse approve(UUID adminId, UUID proofId, String note) {
        String reviewNote = acceptedNote(note, false);
        OffsetDateTime now = OffsetDateTime.now();
        UnderReview reviewed = lockedForDecision(proofId);

        reviewed.proof().approve(adminId, reviewNote, now);
        String detail = reviewNote == null ? "Payment approved." : "Payment approved: " + reviewNote;
        reviewed.booking().recordEvent(BookingEventType.PAYMENT_APPROVED, detail, adminId, now);
        reviewed.booking().confirm(now);
        recordForStudent(OutboxEventType.PAYMENT_APPROVED, reviewed.booking(), detail, now);
        reminders.schedule(reviewed.booking(), now);
        return BookingResponse.from(reviewed.booking());
    }

    @Transactional
    public BookingResponse reject(UUID adminId, UUID proofId, String note) {
        String reviewNote = acceptedNote(note, true);
        OffsetDateTime now = OffsetDateTime.now();
        UnderReview reviewed = lockedForDecision(proofId);

        reviewed.proof().reject(adminId, reviewNote, now);
        reviewed.booking().recordEvent(BookingEventType.PAYMENT_REJECTED, reviewNote, adminId, now);
        reviewed.booking().markPaymentRejected(
                properties.paymentDeadlineFor(reviewed.booking().getTrip().getDepartureAt(), now), now);
        recordForStudent(OutboxEventType.PAYMENT_REJECTED, reviewed.booking(), reviewNote, now);
        return BookingResponse.from(reviewed.booking());
    }

    private void recordForStudent(OutboxEventType type, Booking booking, String detail, OffsetDateTime now) {
        outbox.record(type, booking.getId(), booking.getUserId(),
                BookingNotifications.of(booking, detail), now);
    }

    private record UnderReview(Booking booking, PaymentProof proof) { }

    private UnderReview lockedForDecision(UUID proofId) {
        // Project the booking id, never load the proof, before the lock: a proof loaded here is
        // handed back stale by the read behind the lock.
        UUID bookingId = proofs.findBookingIdById(proofId).orElseThrow(PaymentProofReviewService::proofNotFound);
        Booking booking = bookings.lockById(bookingId).orElseThrow(PaymentProofReviewService::proofNotFound);
        PaymentProof proof = proofs.findById(proofId).orElseThrow(PaymentProofReviewService::proofNotFound);

        if (!proof.isSubmitted()) {
            throw Problems.conflict("payment_proof_already_decided",
                    "That payment proof has already been reviewed.");
        }
        if (!booking.isUnderPaymentReview()) {
            throw Problems.conflict("booking_not_under_review",
                    "That booking is no longer waiting for a payment review.");
        }
        return new UnderReview(booking, proof);
    }

    private static String acceptedNote(String note, boolean required) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw Problems.badRequest("review_note_required",
                        "Say why the payment proof was rejected, so the student can send a better one.");
            }
            return null;
        }
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw Problems.badRequest("review_note_too_long",
                    "A review note must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        return trimmed;
    }

    private byte[] load(String objectKey) {
        try {
            return storage.load(objectKey);
        } catch (RuntimeException failure) {
            throw Problems.serviceUnavailable("payment_proof_unavailable",
                    "The payment proof image could not be read. Please try again.", failure);
        }
    }

    private static RuntimeException proofNotFound() {
        return Problems.notFound("payment_proof_not_found", "Payment proof not found.");
    }
}
