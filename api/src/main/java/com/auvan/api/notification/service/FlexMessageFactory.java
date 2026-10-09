package com.auvan.api.notification.service;

import com.auvan.api.notification.client.FlexMessage;
import com.auvan.api.notification.config.LineMessagingProperties;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.notification.service.NotificationLook.Status;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class FlexMessageFactory {
    private static final String NAVY = "#1b2566";
    private static final String BLUE = "#3041a1";
    private static final String PAPER = "#f6f3ec";
    private static final String TINT = "#c5cbef";
    private static final String MUTED = "#5f6273";
    private static final String RULE = "#d8d1c2";
    private static final String WHITE = "#ffffff";
    private static final int ALT_TEXT_LIMIT = 400;
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.UK)
            .withZone(ZoneId.of("Asia/Bangkok"));

    private final String liffUrl;

    public FlexMessageFactory(LineMessagingProperties properties) {
        this.liffUrl = properties.hasLiffUrl() ? properties.liffUrl().trim() : null;
    }

    private record Ticket(String origin, String destination, OffsetDateTime departureAt, List<String> seats,
                          BigDecimal fare, OffsetDateTime deadline, String explanation, String reference) { }

    public FlexMessage booking(OutboxEventType type, BookingNotification booking) {
        NotificationLook look = NotificationLook.of(type);
        boolean explained = look.detailLabel() != null;
        Ticket ticket = new Ticket(booking.origin(), booking.destination(), booking.departureAt(), booking.seats(),
                booking.fare(), booking.paymentDeadlineAt(), explained ? booking.detail() : null,
                booking.reference());
        String heading = present(booking.reference()) ? "AU-Van booking " + booking.reference() : "AU-Van booking";
        return card(look, ticket, explained ? List.of() : Stream.of(booking.detail()).toList(),
                lines(heading, look.lead(), booking.detail()));
    }

    public FlexMessage waitlist(OutboxEventType type, WaitlistNotification waitlist) {
        NotificationLook look = NotificationLook.of(type);
        Ticket ticket = new Ticket(waitlist.origin(), waitlist.destination(), waitlist.departureAt(),
                waitlist.seats(), waitlist.fare(), waitlist.offerExpiresAt(), null, null);
        return card(look, ticket, Stream.of(waitlist.trip(), waitlist.detail()).toList(),
                lines("AU-Van waitlist", look.lead(), waitlist.trip(), waitlist.detail()));
    }

    private FlexMessage card(NotificationLook look, Ticket ticket, List<String> fallback, String altText) {
        List<Object> body = new ArrayList<>(List.of(pill(look.status()), paragraph(look.lead())));
        if (present(ticket.explanation())) {
            body.add(box("vertical", List.of(text(look.detailLabel(), "xs", MUTED),
                    text(ticket.explanation(), "sm", NAVY, "weight", "bold", "wrap", true))));
        }
        if (present(ticket.origin()) && present(ticket.destination())) {
            body.add(route(ticket.origin(), ticket.destination()));
        } else {
            fallback.stream().filter(FlexMessageFactory::present).map(FlexMessageFactory::paragraph)
                    .forEach(body::add);
        }

        List<Object> rows = new ArrayList<>();
        addRow(rows, "Departs", moment(ticket.departureAt()));
        addRow(rows, "Seats", ticket.seats() == null ? null : String.join(", ", ticket.seats()));
        addRow(rows, "Fare", ticket.fare() == null
                ? null : ticket.fare().setScale(2, RoundingMode.HALF_UP).toPlainString() + " THB");
        if (look.deadlineLabel() != null) {
            addRow(rows, look.deadlineLabel(), moment(ticket.deadline()));
        }
        if (!rows.isEmpty()) {
            body.add(node("type", "separator", "color", RULE, "margin", "lg"));
            body.add(box("vertical", rows, "spacing", "sm"));
        }
        if (present(ticket.reference())) {
            body.add(text("Booking ref " + ticket.reference(), "xxs", MUTED, "margin", "lg"));
        }

        Map<String, Object> bubble = node("type", "bubble", "header", header(look),
                "body", box("vertical", body, "spacing", "md", "backgroundColor", PAPER, "paddingAll", "20px"));
        if (liffUrl != null && look.action() != null) {
            bubble.put("footer", footer(look.action()));
        }
        return new FlexMessage(altText, bubble);
    }

    private static Map<String, Object> header(NotificationLook look) {
        return box("vertical", List.of(
                        text("AU·VAN · " + look.eyebrow(), "xxs", TINT, "weight", "bold"),
                        text(look.title(), "xl", WHITE, "weight", "bold", "wrap", true, "margin", "sm")),
                "backgroundColor", NAVY, "paddingAll", "20px");
    }

    private static Map<String, Object> pill(Status status) {
        return box("horizontal", List.of(box("vertical",
                List.of(text(status.word, "xs", status.foreground, "weight", "bold")),
                "flex", 0, "backgroundColor", status.background, "cornerRadius", "12px",
                "paddingStart", "10px", "paddingEnd", "10px", "paddingTop", "3px", "paddingBottom", "3px")));
    }

    private static Map<String, Object> paragraph(String value) {
        return text(value, "sm", NAVY, "wrap", true);
    }

    private static Map<String, Object> route(String origin, String destination) {
        return box("vertical", List.of(
                stop(dot(true), origin),
                box("vertical", List.of(), "width", "2px", "height", "14px", "backgroundColor", RULE,
                        "offsetStart", "5px"),
                stop(dot(false), destination)), "margin", "lg");
    }

    private static Map<String, Object> stop(Map<String, Object> dot, String place) {
        return box("horizontal", List.of(dot, text(place, "md", NAVY, "weight", "bold", "wrap", true, "flex", 1)),
                "spacing", "md", "alignItems", "center");
    }

    private static Map<String, Object> dot(boolean filled) {
        return box("vertical", List.of(), "flex", 0, "width", "12px", "height", "12px", "cornerRadius", "6px",
                "borderWidth", "2px", "borderColor", NAVY, "backgroundColor", filled ? NAVY : PAPER);
    }

    private static void addRow(List<Object> rows, String label, String value) {
        if (present(value)) {
            rows.add(box("baseline", List.of(text(label, "xs", MUTED, "flex", 2),
                    text(value, "sm", NAVY, "weight", "bold", "wrap", true, "flex", 5)), "spacing", "md"));
        }
    }

    private Map<String, Object> footer(String action) {
        return box("vertical", List.of(node("type", "button", "style", "primary", "color", BLUE, "height", "sm",
                        "action", node("type", "uri", "label", action, "uri", liffUrl))),
                "backgroundColor", PAPER, "paddingAll", "16px");
    }

    private static Map<String, Object> text(String value, String size, String color, Object... extra) {
        Map<String, Object> text = node("type", "text", "text", value, "size", size, "color", color);
        text.putAll(node(extra));
        return text;
    }

    private static Map<String, Object> box(String layout, List<?> contents, Object... extra) {
        Map<String, Object> box = node("type", "box", "layout", layout, "contents", contents);
        box.putAll(node(extra));
        return box;
    }

    private static Map<String, Object> node(Object... pairs) {
        Map<String, Object> node = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            node.put((String) pairs[i], pairs[i + 1]);
        }
        return node;
    }

    private static String moment(OffsetDateTime value) {
        return value == null ? null : MOMENT.format(value);
    }

    private static String lines(String... parts) {
        String text = Stream.of(parts).filter(FlexMessageFactory::present).collect(Collectors.joining("\n"));
        return text.length() <= ALT_TEXT_LIMIT ? text : text.substring(0, ALT_TEXT_LIMIT - 1) + "…";
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
