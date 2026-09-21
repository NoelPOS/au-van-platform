package com.auvan.api.notification.service;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.client.LineMessageSender;
import com.auvan.api.notification.client.LinePushMessage;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.PermanentFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Turns one outbox row into one message, and is the only thing that knows what
 * a message says.
 *
 * <p>The sender is optional on purpose. {@code notification.line.enabled} is
 * what decides whether an implementation of {@link LineMessageSender} exists at
 * all, and with it off — which is how the whole test suite runs — the two other
 * answers are both wrong: failing would burn every row's attempt budget and
 * fill the table with dead letters describing a dependency nobody asked for,
 * and pretending to send would hide it. So the omission is logged once per
 * event and the row is resolved.
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
        // Composed before the sender check, so a row whose payload does not
        // match its type fails here rather than being resolved in silence.
        String text = textFor(event);
        if (sender.isEmpty()) {
            log.info("notification.line.enabled is off: {} for {} is recorded but not delivered.",
                    event.getEventType(), event.getAggregateId());
            return;
        }
        // Permanent, not transient: a user row that has gone will not come back,
        // and retrying the lookup four more times only delays the dead letter
        // that says so.
        AppUser recipient = users.findById(event.getRecipientUserId())
                .orElseThrow(() -> new PermanentFailureException(
                        "Outbox event " + event.getId() + " names a recipient that no longer exists."));
        // The retry key is the row's id and never changes across retries, which
        // is what keeps an at-least-once transport from producing a second
        // message the student sees.
        sender.get().send(new LinePushMessage(recipient.getLineSubject(), text, event.getId().toString()));
    }

    /**
     * One lead sentence per event type, and the payload shape each type's arm
     * names. The switch is exhaustive and has no {@code default}, so a new
     * {@link OutboxEventType} fails to compile until somebody decides what it
     * says and which payload it carries.
     */
    private String textFor(OutboxEvent event) {
        return switch (event.getEventType()) {
            case BOOKING_CREATED ->
                    booking(event, "Your seats are held. Send your payment proof to keep them.");
            case BOOKING_CANCELLED -> booking(event, "Your booking has been cancelled.");
            case BOOKING_EXPIRED ->
                    booking(event, "Your booking was not paid for in time, so the seats have been released.");
            case PAYMENT_PROOF_SUBMITTED -> booking(event, "We have your payment proof and are reviewing it.");
            case PAYMENT_APPROVED -> booking(event, "Your payment is approved and your booking is confirmed.");
            case PAYMENT_REJECTED -> booking(event, "Your payment proof was not accepted.");
            // The two reminders say how long is left rather than what happened,
            // because nothing has: they were scheduled when the booking was
            // approved and this is simply their time (ADR-010).
            case DEPARTURE_REMINDER_24H -> booking(event, "Your trip departs in 24 hours.");
            case DEPARTURE_REMINDER_1H -> booking(event, "Your trip departs in 1 hour.");
            // The two waitlist rows carry a WaitlistNotification, not a
            // BookingNotification: a promotion happens before any booking
            // exists, so there is no reference to quote (ADR-011).
            case WAITLIST_PROMOTED ->
                    waitlist(event, "A seat has come free on a trip you were waiting for and is held for you.");
            case WAITLIST_PROMOTION_EXPIRED ->
                    waitlist(event, "Your waitlisted seat was not taken in time, so it has gone to the next student.");
        };
    }

    private String booking(OutboxEvent event, String lead) {
        BookingNotification booking = json.readValue(event.getPayload(), BookingNotification.class);
        return "AU-Van booking " + booking.reference() + "\n" + lead + "\n" + booking.detail();
    }

    private String waitlist(OutboxEvent event, String lead) {
        WaitlistNotification waitlist = json.readValue(event.getPayload(), WaitlistNotification.class);
        return "AU-Van waitlist\n" + lead + "\n" + waitlist.trip() + "\n" + waitlist.detail();
    }
}
