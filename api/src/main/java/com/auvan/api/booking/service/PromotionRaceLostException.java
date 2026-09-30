package com.auvan.api.booking.service;

// Thrown, not returned as false: the constraint violation already made the transaction
// rollback-only. Catch only this, so any other insert failure still escapes the sweep.
class PromotionRaceLostException extends RuntimeException {
    PromotionRaceLostException(Throwable cause) {
        super("A promoter lost the race for its seats to another claim.", cause);
    }
}
