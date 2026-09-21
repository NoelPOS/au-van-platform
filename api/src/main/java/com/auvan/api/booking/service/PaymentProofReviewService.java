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
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The administrator's side of a payment proof: the queue, the image, and the
 * decision that moves the booking to {@code CONFIRMED} or
 * {@code PAYMENT_REJECTED}.
 *
 * <p>A decision is one transaction that opens by locking the <em>booking's</em>
 * row, exactly as {@link PaymentProofService#submit} does, because it is the
 * same read-then-write on {@code bookings.status} that no constraint can see.
 * The administrator is not the booking's owner, so the lock is
 * {@link BookingRepository#lockById} rather than its owner-scoped twin.
 *
 * <p>The order inside {@link #lockedForDecision} is load-bearing and is the one
 * non-obvious thing in this class; its own comment says why.
 */
@Service
public class PaymentProofReviewService {
    /** The legacy application's own ceiling ({@code payment.validator.ts}), and {@code V6}'s column width. */
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

    /** Everything still waiting for a decision, oldest first. */
    @Transactional(readOnly = true)
    public List<PaymentProofResponse> list() {
        return proofs.findByStatusOrderByCreatedAt(PaymentProofStatus.SUBMITTED).stream()
                .map(PaymentProofResponse::from)
                .toList();
    }

    /** The stored image and the type it was stored as. */
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
        // Approval is the moment a trip becomes something to be reminded about,
        // and it is inside this transaction so an approval that rolls back
        // schedules nothing. Whichever of the two reminders is already behind
        // the booking is not queued at all.
        reminders.schedule(reviewed.booking(), now);
        return BookingResponse.from(reviewed.booking());
    }

    @Transactional
    public BookingResponse reject(UUID adminId, UUID proofId, String note) {
        String reviewNote = acceptedNote(note, true);
        OffsetDateTime now = OffsetDateTime.now();
        UnderReview reviewed = lockedForDecision(proofId);

        reviewed.proof().reject(adminId, reviewNote, now);
        // The note is the whole point of rejecting: the student may resubmit,
        // and without a reason they will send the same blurred slip again.
        reviewed.booking().recordEvent(BookingEventType.PAYMENT_REJECTED, reviewNote, adminId, now);
        // A fresh window, measured from now. The student has been told to send
        // a better slip, and the deadline they were under while the first one
        // sat in a queue would give them no time at all to send one.
        reviewed.booking().markPaymentRejected(
                properties.paymentDeadlineFor(reviewed.booking().getTrip().getDepartureAt(), now), now);
        recordForStudent(OutboxEventType.PAYMENT_REJECTED, reviewed.booking(), reviewNote, now);
        return BookingResponse.from(reviewed.booking());
    }

    /**
     * The decision is the administrator's; the news is the student's. The
     * recipient is the booking's owner and never {@code adminId}, and it is
     * recorded inside the decision's own transaction, so a decision that rolls
     * back tells nobody anything.
     */
    private void recordForStudent(OutboxEventType type, Booking booking, String detail, OffsetDateTime now) {
        outbox.record(type, booking.getId(), booking.getUserId(),
                new BookingNotification(booking.getReference(), detail), now);
    }

    /** A proof that may still be decided, and the locked booking it belongs to. */
    private record UnderReview(Booking booking, PaymentProof proof) { }

    private UnderReview lockedForDecision(UUID proofId) {
        // The request names a proof; the row that has to be locked is its
        // booking. Reading the booking id as a scalar rather than through the
        // PaymentProof entity is what keeps the lock real: loading the entity
        // here would put it — and its booking — in the persistence context
        // before the lock, and the read below would hand that pre-lock instance
        // straight back, still saying SUBMITTED after a rival's approval had
        // committed. The guard would go on looking exactly like a guard.
        UUID bookingId = proofs.findBookingIdById(proofId).orElseThrow(PaymentProofReviewService::proofNotFound);
        Booking booking = bookings.lockById(bookingId).orElseThrow(PaymentProofReviewService::proofNotFound);
        // Only now, and only from behind the lock.
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

    /**
     * Trims the note, refuses one that is too long, and refuses a missing one
     * when a note is required. Returns {@code null} for "no note", so a blank
     * string never reaches the column.
     */
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
            // Without this the SDK's own exception escapes as a bare 500 whose
            // body names the bucket and the endpoint, exactly as the submit
            // path already guards against.
            throw Problems.serviceUnavailable("payment_proof_unavailable",
                    "The payment proof image could not be read. Please try again.", failure);
        }
    }

    private static RuntimeException proofNotFound() {
        return Problems.notFound("payment_proof_not_found", "Payment proof not found.");
    }
}
