package com.auvan.api.notification.service;

import com.auvan.api.notification.client.FlexMessage;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.auvan.api.notification.service.FlexCards.LIFF_URL;
import static com.auvan.api.notification.service.FlexCards.factory;
import static com.auvan.api.notification.service.FlexCards.isWaitlist;
import static com.auvan.api.notification.service.FlexCards.json;
import static com.auvan.api.notification.service.FlexCards.oldBooking;
import static com.auvan.api.notification.service.FlexCards.oldWaitlist;
import static com.auvan.api.notification.service.FlexCards.render;
import static com.auvan.api.notification.service.FlexCards.texts;
import static com.auvan.api.notification.service.FlexCards.tree;
import static org.assertj.core.api.Assertions.assertThat;

class FlexMessageLimitsTests {
    private static final Set<String> COMPONENTS = Set.of("bubble", "box", "text", "separator", "button", "uri");
    private static final Set<String> LAYOUTS = Set.of("horizontal", "vertical", "baseline");
    private static final Set<String> SIZES =
            Set.of("xxs", "xs", "sm", "md", "lg", "xl", "xxl", "3xl", "4xl", "5xl");
    private static final Set<String> SPACES = Set.of("none", "xs", "sm", "md", "lg", "xl", "xxl");
    private static final Set<String> COLOURS = Set.of("color", "backgroundColor", "borderColor");
    private static final String PIXELS = "\\d+px";

    @Test
    void everyCardOfEveryShapeIsAValidBubbleInsideLinesLimits() {
        for (FlexMessage message : everyCard()) {
            JsonNode contents = tree(message).path("contents");

            assertThat(message.type()).isEqualTo("flex");
            assertThat(message.altText()).isNotBlank().hasSizeLessThanOrEqualTo(400);
            assertThat(contents.path("type").asString()).isEqualTo("bubble");
            assertThat(json(message).getBytes(StandardCharsets.UTF_8).length).isLessThan(30 * 1024);
            walk(contents, message.altText());
        }
    }

    @Test
    void everyCardNamesItsStatusInWordsBesideItsColour() {
        for (OutboxEventType type : OutboxEventType.values()) {
            NotificationLook.Status status = NotificationLook.of(type).status();
            FlexMessage message = render(factory(LIFF_URL), type, FlexCards.payloadFor(type));

            assertThat(texts(message)).as(type.name()).contains(status.word);
            assertThat(tree(message).findValuesAsString("backgroundColor")).as(type.name())
                    .contains(status.background);
        }
    }

    @Test
    void everyEventHasItsOwnTitle() {
        List<String> titles = new ArrayList<>();
        for (OutboxEventType type : OutboxEventType.values()) {
            titles.add(tree(render(factory(""), type, FlexCards.payloadFor(type)))
                    .path("contents").path("header").path("contents").get(1).path("text").asString());
        }

        assertThat(titles).doesNotHaveDuplicates().allMatch(title -> !title.isBlank());
    }

    private static List<FlexMessage> everyCard() {
        List<FlexMessage> cards = new ArrayList<>();
        for (OutboxEventType type : OutboxEventType.values()) {
            for (String liffUrl : new String[] {LIFF_URL, ""}) {
                cards.add(render(factory(liffUrl), type, FlexCards.payloadFor(type)));
                cards.add(render(factory(liffUrl), type, isWaitlist(type) ? oldWaitlist() : oldBooking("Old.")));
            }
        }
        return cards;
    }

    private static void walk(JsonNode node, String card) {
        String type = node.path("type").asString();
        assertThat(COMPONENTS).as(card).contains(type);
        switch (type) {
            case "box" -> {
                assertThat(LAYOUTS).as(card).contains(node.path("layout").asString());
                assertThat(node.path("contents").isArray()).as(card).isTrue();
            }
            case "text" -> assertThat(node.path("text").asString()).as(card).isNotBlank();
            case "uri" -> {
                assertThat(node.path("label").asString()).as(card).isNotBlank().hasSizeLessThanOrEqualTo(20);
                assertThat(node.path("uri").asString()).as(card).startsWith("https://");
            }
            default -> { }
        }
        for (Map.Entry<String, JsonNode> property : node.properties()) {
            String name = property.getKey();
            JsonNode value = property.getValue();
            if (COLOURS.contains(name)) {
                assertThat(value.asString()).as(card + " " + name).matches("#[0-9a-f]{6}");
            } else if (name.equals("size")) {
                assertThat(SIZES).as(card).contains(value.asString());
            } else if (name.equals("margin") || name.equals("spacing")) {
                assertThat(SPACES).as(card).contains(value.asString());
            } else if (name.startsWith("padding") || name.equals("cornerRadius") || name.equals("width")
                    || name.equals("height") && !type.equals("button") || name.startsWith("offset")
                    || name.equals("borderWidth")) {
                assertThat(value.asString()).as(card + " " + name).matches(PIXELS);
            } else if (value.isObject()) {
                walk(value, card);
            } else if (value.isArray()) {
                value.forEach(child -> walk(child, card));
            }
        }
    }
}
