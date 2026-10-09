package com.auvan.api.booking.exception;

// Thrown because the violation made the transaction rollback-only; catch only this one.
public class PromotionRaceLostException extends RuntimeException {
    public PromotionRaceLostException(Throwable cause) {
        super("A promoter lost the race for its seats to another claim.", cause);
    }
}
