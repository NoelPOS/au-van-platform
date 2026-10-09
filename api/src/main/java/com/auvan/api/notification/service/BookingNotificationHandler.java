package com.auvan.api.notification.service;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.client.FlexMessage;
import com.auvan.api.notification.client.LineMessageSender;
import com.auvan.api.notification.client.LinePushMessage;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.exception.PermanentFailureException;
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
    private final FlexMessageFactory cards;

    public BookingNotificationHandler(Optional<LineMessageSender> sender, AppUserRepository users,
                                      ObjectMapper json, FlexMessageFactory cards) {
        this.sender = sender;
        this.users = users;
        this.json = json;
        this.cards = cards;
    }

    public void handle(OutboxEvent event) {
        FlexMessage message = messageFor(event);
        if (sender.isEmpty()) {
            log.info("notification.line.enabled is off: {} for {} is recorded but not delivered.",
                    event.getEventType(), event.getAggregateId());
            return;
        }
        AppUser recipient = users.findById(event.getRecipientUserId())
                .orElseThrow(() -> new PermanentFailureException(
                        "Outbox event " + event.getId() + " names a recipient that no longer exists."));
        // The retry key is the row id; it must not change across retries.
        sender.get().send(new LinePushMessage(recipient.getLineSubject(), message, event.getId().toString()));
    }

    private FlexMessage messageFor(OutboxEvent event) {
        OutboxEventType type = event.getEventType();
        return switch (type) {
            case BOOKING_CREATED, BOOKING_CANCELLED, BOOKING_EXPIRED, PAYMENT_PROOF_SUBMITTED, PAYMENT_APPROVED,
                 PAYMENT_REJECTED, DEPARTURE_REMINDER_24H, DEPARTURE_REMINDER_1H, TRIP_CANCELLED, TRIP_RESCHEDULED ->
                    cards.booking(type, json.readValue(event.getPayload(), BookingNotification.class));
            case WAITLIST_PROMOTED, WAITLIST_PROMOTION_EXPIRED ->
                    cards.waitlist(type, json.readValue(event.getPayload(), WaitlistNotification.class));
        };
    }
}
