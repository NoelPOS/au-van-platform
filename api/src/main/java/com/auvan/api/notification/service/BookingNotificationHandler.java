package com.auvan.api.notification.service;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.client.LineMessageSender;
import com.auvan.api.notification.client.LinePushMessage;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.service.PermanentFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

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

    public void handle(OutboxEvent event) {
        String text = textFor(event);
        if (sender.isEmpty()) {
            log.info("notification.line.enabled is off: {} for {} is recorded but not delivered.",
                    event.getEventType(), event.getAggregateId());
            return;
        }
        AppUser recipient = users.findById(event.getRecipientUserId())
                .orElseThrow(() -> new PermanentFailureException(
                        "Outbox event " + event.getId() + " names a recipient that no longer exists."));
        // The retry key is the row id; it must not change across retries.
        sender.get().send(new LinePushMessage(recipient.getLineSubject(), text, event.getId().toString()));
    }

    // No default branch, so a new event type fails to compile here.
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
            case DEPARTURE_REMINDER_24H -> booking(event, "Your trip departs in 24 hours.");
            case DEPARTURE_REMINDER_1H -> booking(event, "Your trip departs in 1 hour.");
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
