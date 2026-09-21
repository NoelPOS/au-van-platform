package com.auvan.api.notification.client;

/**
 * Where a push message goes. The port exists so the dispatcher can be exercised
 * — and made to fail — without a LINE channel, the same shape
 * {@code PaymentProofStorage} uses for object storage.
 *
 * <p>There are two implementations: {@link LineMessageSenderImpl} against the
 * Messaging API, and a recording fake in test sources. The real one exists only
 * when {@code notification.line.enabled} is true; with it off no bean implements
 * this and {@code BookingNotificationHandler} records the omission instead of
 * sending, which is how the suite runs with no channel and no credential.
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
