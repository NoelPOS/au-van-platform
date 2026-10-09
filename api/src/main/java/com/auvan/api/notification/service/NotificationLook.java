package com.auvan.api.notification.service;

import com.auvan.api.outbox.entity.OutboxEventType;

record NotificationLook(String eyebrow, String title, String lead, Status status, String action,
                        String deadlineLabel, String detailLabel) {
    private static final String UPLOAD = "Upload payment slip";
    private static final String VIEW = "View booking";

    enum Status {
        CONFIRMED("Confirmed", "#2e7a57", "#ffffff"),
        ACTION_NEEDED("Action needed", "#d99a2b", "#1b2566"),
        HELD("Held for you", "#d99a2b", "#1b2566"),
        IN_REVIEW("In review", "#3041a1", "#ffffff"),
        REMINDER("Reminder", "#3041a1", "#ffffff"),
        NOT_ACCEPTED("Not accepted", "#b23b2e", "#ffffff"),
        CANCELLED("Cancelled", "#b23b2e", "#ffffff"),
        RESCHEDULED("New time", "#3041a1", "#ffffff"),
        EXPIRED("Expired", "#b23b2e", "#ffffff");

        final String word;
        final String background;
        final String foreground;

        Status(String word, String background, String foreground) {
            this.word = word;
            this.background = background;
            this.foreground = foreground;
        }
    }

    // No default branch, so a new event type fails to compile here.
    static NotificationLook of(OutboxEventType type) {
        return switch (type) {
            case BOOKING_CREATED -> new NotificationLook("BOOKING", "Seats held",
                    "Your seats are held. Send your payment proof to keep them.",
                    Status.ACTION_NEEDED, UPLOAD, "Pay by", null);
            case BOOKING_CANCELLED -> new NotificationLook("BOOKING", "Booking cancelled",
                    "Your booking has been cancelled.", Status.CANCELLED, null, null, null);
            case BOOKING_EXPIRED -> new NotificationLook("BOOKING", "Booking expired",
                    "Your booking was not paid for in time, so the seats have been released.",
                    Status.EXPIRED, null, null, null);
            case PAYMENT_PROOF_SUBMITTED -> new NotificationLook("PAYMENT", "Payment slip received",
                    "We have your payment proof and are reviewing it.", Status.IN_REVIEW, null, null, null);
            case PAYMENT_APPROVED -> new NotificationLook("PAYMENT", "Payment approved",
                    "Your payment is approved and your booking is confirmed.", Status.CONFIRMED, VIEW, null, null);
            case PAYMENT_REJECTED -> new NotificationLook("PAYMENT", "Payment slip not accepted",
                    "Your payment proof was not accepted.", Status.NOT_ACCEPTED, UPLOAD, "Pay by", "Reason");
            case DEPARTURE_REMINDER_24H -> new NotificationLook("REMINDER", "Departs in 24 hours",
                    "Your trip departs in 24 hours.", Status.REMINDER, VIEW, null, null);
            case DEPARTURE_REMINDER_1H -> new NotificationLook("REMINDER", "Departs in 1 hour",
                    "Your trip departs in 1 hour.", Status.REMINDER, VIEW, null, null);
            case WAITLIST_PROMOTED -> new NotificationLook("WAITLIST", "A seat is free for you",
                    "A seat has come free on a trip you were waiting for and is held for you.",
                    Status.HELD, "Take the seat", "Take by", null);
            case WAITLIST_PROMOTION_EXPIRED -> new NotificationLook("WAITLIST", "Waitlist offer ended",
                    "Your waitlisted seat was not taken in time, so it has gone to the next student.",
                    Status.EXPIRED, null, "Offer ended", null);
            case TRIP_CANCELLED -> new NotificationLook("TRIP", "Trip cancelled",
                    "AU-Van has cancelled this trip. If you paid, your fare will be refunded.",
                    Status.CANCELLED, null, null, "Reason");
            case TRIP_RESCHEDULED -> new NotificationLook("TRIP", "Departure time changed",
                    "Your trip now leaves at a new time.", Status.RESCHEDULED, VIEW, null, "What changed");
        };
    }
}
