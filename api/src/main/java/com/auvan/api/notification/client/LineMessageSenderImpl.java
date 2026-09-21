package com.auvan.api.notification.client;

import com.auvan.api.notification.config.LineMessagingProperties;
import com.auvan.api.outbox.service.PermanentFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * The real push, against the LINE Messaging API.
 *
 * <p>Uses {@code RestClient}, as {@code LineTokenVerifierImpl} already does, so
 * this adds no dependency. It builds its own builder for the same reason that
 * class does: Spring Boot 4 moved {@code RestClientAutoConfiguration} into a
 * module this project does not depend on, so there is no
 * {@code RestClient.Builder} bean to inject and asking for one fails at
 * startup. The second constructor below exists so a test can supply a builder
 * bound to a {@code MockRestServiceServer}, which is what lets the status-code
 * classification be proven against real responses instead of asserted about in
 * prose.
 *
 * <p><strong>The classification is the whole of this class.</strong> LINE's own
 * guidance on retrying is that {@code 500} and a timeout are safe to retry and
 * that {@code 4xx} and {@code 409} are not, and the three cases are handled
 * separately because they mean different things to a queue:
 *
 * <ul>
 * <li>{@code 409} is <em>success</em>. It is LINE saying it has already accepted
 *     a request carrying this retry key, which is exactly what the retry key is
 *     for: the earlier attempt reached the student even though this worker never
 *     saw the answer. Treating it as a failure would dead-letter a message that
 *     was in fact delivered.</li>
 * <li>{@code 429} and {@code 5xx} are transient. A rate limit clears and a
 *     server error passes, so the row backs off and is tried again.</li>
 * <li>Every other {@code 4xx} is permanent. {@code 404} is the one that
 *     actually happens — an unknown user id, or a student who has not added the
 *     official account as a friend — and it will answer the same way forever, so
 *     the row dies on this attempt rather than on the fifth.</li>
 * </ul>
 *
 * <p>The {@code 429} judgement is the one place this departs from a literal
 * reading of LINE's "do not retry 4xx": a rate limit describes this moment, not
 * the request, and dead-lettering a notification because the channel was busy
 * for a second would be wrong. {@code 400}, {@code 401}, {@code 403} and
 * {@code 404} all describe the request or the recipient, and those are final.
 */
@Component
@ConditionalOnProperty(prefix = "notification.line", name = "enabled", havingValue = "true")
class LineMessageSenderImpl implements LineMessageSender {
    /**
     * LINE's documented deduplication header. Its value must be a hexadecimal
     * UUID, which the outbox row's own id already is, and LINE honours it for
     * twenty-four hours — {@code outbox.max-attempts} and {@code backoff-cap}
     * are set so the whole retry schedule finishes far inside that window, and
     * {@code OutboxProperties} records the arithmetic.
     */
    private static final String RETRY_KEY_HEADER = "X-Line-Retry-Key";
    private static final int ALREADY_ACCEPTED = 409;
    private static final int TOO_MANY_REQUESTS = 429;

    private static final Logger log = LoggerFactory.getLogger(LineMessageSenderImpl.class);

    private final RestClient lineClient;
    private final LineMessagingProperties properties;

    @Autowired
    LineMessageSenderImpl(LineMessagingProperties properties) {
        this(properties, RestClient.builder());
    }

    /** Visible for tests, which pass a builder bound to a mock server. */
    LineMessageSenderImpl(LineMessagingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.lineClient = builder.baseUrl(properties.apiBaseUrl()).build();
    }

    @Override
    public void send(LinePushMessage message) {
        if (!properties.hasChannelAccessToken()) {
            // Transient on purpose, and the precedent is LineTokenVerifierImpl's
            // blank-channel-id check: the application still starts, and a token
            // supplied later makes the next retry work. Failing at startup would
            // stop an instance booting over a channel most of it does not use.
            throw new IllegalStateException(
                    "LINE_CHANNEL_ACCESS_TOKEN must be configured before a push can be delivered.");
        }
        if (message.to() == null || message.to().isBlank()) {
            // Nothing to send it to, and no later attempt invents one.
            throw new PermanentFailureException(
                    "The recipient has no LINE user id, so no push can ever be addressed to them.");
        }

        try {
            lineClient.post()
                    .uri("/v2/bot/message/push")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.channelAccessToken())
                    .header(RETRY_KEY_HEADER, message.retryKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PushRequest(message.to(), List.of(new TextMessage(message.text()))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException answered) {
            classify(answered, message);
        } catch (RestClientException unanswered) {
            // No status at all: a connection refused, a timeout, a body that
            // would not parse. LINE may or may not have the message, which is
            // exactly the case the retry key exists for, so retry.
            throw new IllegalStateException("The LINE push could not be completed.", unanswered);
        }
    }

    /** Returns normally only when the response, despite being an error, means the student was told. */
    private static void classify(RestClientResponseException answered, LinePushMessage message) {
        int status = answered.getStatusCode().value();
        if (status == ALREADY_ACCEPTED) {
            log.info("LINE had already accepted retry key {}; the student has the message. Not sending again.",
                    message.retryKey());
            return;
        }
        if (status == TOO_MANY_REQUESTS || answered.getStatusCode().is5xxServerError()) {
            throw new IllegalStateException("LINE answered " + status + " for retry key " + message.retryKey()
                    + ": " + answered.getResponseBodyAsString(), answered);
        }
        if (answered.getStatusCode().is4xxClientError()) {
            throw new PermanentFailureException("LINE answered " + status + " for retry key " + message.retryKey()
                    + ", which no retry can change: " + answered.getResponseBodyAsString(), answered);
        }
        throw new IllegalStateException("LINE answered " + status + " for retry key " + message.retryKey()
                + ": " + answered.getResponseBodyAsString(), answered);
    }

    /** LINE's push body: one recipient, and the messages to send them. */
    private record PushRequest(String to, List<TextMessage> messages) { }

    /** {@code type} is part of LINE's wire format, not a field this system chose. */
    private record TextMessage(String type, String text) {
        TextMessage(String text) {
            this("text", text);
        }
    }
}
