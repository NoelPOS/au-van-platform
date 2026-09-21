package com.auvan.api.booking.service;

/**
 * A promoter's claim insert was refused by {@code seat_claims_trip_seat_unique}
 * because somebody took the seat first.
 *
 * <p>The ordinary outcome of a lost race and not an error: the entry stays
 * {@code WAITING} and the next sweep tries again. It is a throw rather than a
 * {@code false} because the constraint violation has already marked
 * {@link WaitlistPromotionWriter}'s transaction rollback-only — returning
 * normally would fail at commit instead, with the promotion half-applied in
 * memory. Rolling back is what leaves the entry alone.
 *
 * <p>Narrow on purpose. {@link WaitlistPromotionService} catches this and
 * nothing else, so an insert that fails for any other reason still escapes the
 * sweep and is logged by the scheduler rather than being swallowed as a lost
 * race.
 */
class PromotionRaceLostException extends RuntimeException {
    PromotionRaceLostException(Throwable cause) {
        super("A promoter lost the race for its seats to another claim.", cause);
    }
}
