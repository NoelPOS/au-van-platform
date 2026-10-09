package com.auvan.api.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_proofs", uniqueConstraints =
        @UniqueConstraint(name = "payment_proofs_object_key_unique", columnNames = "object_key"))
public class PaymentProof {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "submitted_by_user_id", nullable = false)
    private UUID submittedByUserId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProofStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    protected PaymentProof() { }

    public PaymentProof(Booking booking, UUID submittedByUserId, String objectKey, String contentType,
                        long sizeBytes, String contentSha256, OffsetDateTime now) {
        this.booking = booking;
        this.submittedByUserId = submittedByUserId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.contentSha256 = contentSha256;
        this.status = PaymentProofStatus.SUBMITTED;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public UUID getSubmittedByUserId() { return submittedByUserId; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getContentSha256() { return contentSha256; }
    public PaymentProofStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }

    public boolean isSubmitted() {
        return status == PaymentProofStatus.SUBMITTED;
    }

    public void approve(UUID reviewerId, String note, OffsetDateTime now) {
        decide(PaymentProofStatus.APPROVED, reviewerId, note, now);
    }

    public void reject(UUID reviewerId, String note, OffsetDateTime now) {
        decide(PaymentProofStatus.REJECTED, reviewerId, note, now);
    }

    private void decide(PaymentProofStatus decision, UUID reviewerId, String note, OffsetDateTime now) {
        this.status = decision;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = note;
    }
}
