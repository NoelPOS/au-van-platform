package com.auvan.api.notification.service;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.client.LineMessageSender;
import com.auvan.api.notification.client.LinePushMessage;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Turns one outbox row into one message, and is the only thing that knows what
 * a message says.
 *
 * <p>The sender is optional on purpose. Until #63 supplies an implementation of
 * {@link LineMessageSender} nothing here can reach a phone, and the two other
 * answers are both wrong: failing would burn every row's attempt budget and
 * fill the table with dead letters describing an absent dependency, and
 * pretending to send would hide it. So the omission is logged once per event
 * and the row is resolved — the work was recorded, which is this issue's whole
 * claim, and delivering it is #63's.
 */
@Service
public class BookingNotificationHandler {
    private static final Logger log = LoggerFactory.getLogger(BookingNotificationHandler.class);

    private final Optional<LineMessageSender> sender;
    private final AppUserRepository users;
    private final ObjectMapper json;

    public BookingNotificationHandler(Optional<LineMessageSender> sender, AppUserRepository users,
                                      ObjectMapper json) {
        this.sender = sender;
        this.users = users;
        this.json = json;
    }

    /**
     * @throws RuntimeException if the message could not be delivered; the
     *                          dispatcher retries and eventually dead-letters
     */
    public void handle(OutboxEvent event) {
        BookingNotification booking = json.readValue(event.getPayload(), BookingNotification.class);
        if (sender.isEmpty()) {
            log.info("No LineMessageSender is configured: {} for booking {} is recorded but not delivered (#63).",
                    event.getEventType(), event.getAggregateId());
            return;
        }
        AppUser recipient = users.findById(event.getRecipientUserId()).orElseThrow(() -> new IllegalStateException(
                "Outbox event " + event.getId() + " names a recipient that no longer exists."));
        // The retry key is the row's id and never changes across retries, which
        // is what keeps an at-least-once transport from producing a second
        // message the student sees.
        sender.get().send(new LinePushMessage(recipient.getLineSubject(),
                textFor(event.getEventType(), booking), event.getId().toString()));
    }

    private static String textFor(OutboxEventType type, BookingNotification booking) {
        String lead = switch (type) {
            case BOOKING_CREATED -> "Your seats are held. Send your payment proof to keep them.";
            case BOOKING_CANCELLED -> "Your booking has been cancelled.";
            case BOOKING_EXPIRED ->
                    "Your booking was not paid for in time, so the seats have been released.";
            case PAYMENT_PROOF_SUBMITTED -> "We have your payment proof and are reviewing it.";
            case PAYMENT_APPROVED -> "Your payment is approved and your booking is confirmed.";
            case PAYMENT_REJECTED -> "Your payment proof was not accepted.";
        };
        return "AU-Van booking " + booking.reference() + "\n" + lead + "\n" + booking.detail();
    }
}
