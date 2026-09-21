package com.auvan.api.notification.client;

/**
 * Where a push message goes. The port exists so the dispatcher can be exercised
 * — and made to fail — without a LINE channel, the same shape
 * {@code PaymentProofStorage} uses for object storage.
 *
 * <p>There are two implementations: a recording fake in test sources, and, once
 * #63 lands, one against the Messaging API. Until then no bean implements this
 * and {@code BookingNotificationHandler} records the omission instead of
 * sending, which is why nothing in this issue is user-visible.
 */
public interface LineMessageSender {
    /**
     * Sends the message, carrying {@code retryKey} so that a retry of the same
     * outbox row cannot become a second message the student sees.
     *
     * @throws RuntimeException if the message was not accepted; the dispatcher
     *                          turns that into a backed-off retry and, once the
     *                          attempt budget is spent, a dead letter
     */
    void send(LinePushMessage message);
}
