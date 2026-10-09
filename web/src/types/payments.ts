export type PaymentProofStatus = "SUBMITTED" | "APPROVED" | "REJECTED";

export type SameSlipBooking = {
  bookingId: string;
  bookingReference: string;
};

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
  sameSlipBookings: SameSlipBooking[];
};
