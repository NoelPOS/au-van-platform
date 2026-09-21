package com.auvan.api.outbox.service;

/**
 * A failure that retrying cannot fix, so {@link OutboxDispatcher} dead-letters
 * the row on the attempt that raised it instead of spending the rest of the
 * budget on it.
 *
 * <p>It lives here, next to the dispatcher, because it is the dispatcher's own
 * vocabulary rather than any one handler's: a handler says "this will never
 * succeed" and the retry policy that acts on that stays in one place. Every
 * other {@code RuntimeException} means "it might work next time" and is backed
 * off and retried.
 *
 * <p>The case that forces this to exist is a student who has not added the
 * LINE official account as a friend. LINE answers {@code 404} and will go on
 * answering {@code 404} for as long as that is true, so without this every such
 * student generates {@code outbox.max-attempts} failed sends for every single
 * notification — a dead letter is the honest record, and it is one row instead
 * of five attempts.
 */
public class PermanentFailureException extends RuntimeException {
    public PermanentFailureException(String message) {
        super(message);
    }

    public PermanentFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
