export type PaymentProofStatus = "SUBMITTED" | "APPROVED" | "REJECTED";

/**
 * One row of the review queue. There is no image URL here on purpose: the
 * bytes are fetched from the API by the proof's own id, with the
 * administrator's token (ADR-009).
 */
export type PaymentProof = {
  id: string;
  bookingId: string;
  bookingReference: string;
  passengerName: string;
  passengerPhone: string;
  totalFare: number;
  trip: {
    id: string;
    origin: string;
    destination: string;
    departureAt: string;
  };
  submittedByUserId: string;
  contentType: string;
  sizeBytes: number;
  status: PaymentProofStatus;
  submittedAt: string;
};
