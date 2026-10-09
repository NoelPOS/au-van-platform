package com.auvan.api.notification.client;

import com.auvan.api.notification.config.LineMessagingProperties;
import com.auvan.api.outbox.exception.PermanentFailureException;
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

@Component
@ConditionalOnProperty(prefix = "notification.line", name = "enabled", havingValue = "true")
class LineMessageSenderImpl implements LineMessageSender {
    // LINE dedupes on this header for 24 hours.
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

    LineMessageSenderImpl(LineMessagingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.lineClient = builder.baseUrl(properties.apiBaseUrl()).build();
    }

    @Override
    public void send(LinePushMessage message) {
        if (!properties.hasChannelAccessToken()) {
            throw new IllegalStateException(
                    "LINE_CHANNEL_ACCESS_TOKEN must be configured before a push can be delivered.");
        }
        if (message.to() == null || message.to().isBlank()) {
            throw new PermanentFailureException(
                    "The recipient has no LINE user id, so no push can ever be addressed to them.");
        }

        try {
            lineClient.post()
                    .uri("/v2/bot/message/push")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.channelAccessToken())
                    .header(RETRY_KEY_HEADER, message.retryKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PushRequest(message.to(), List.of(message.message())))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException answered) {
            classify(answered, message);
        } catch (RestClientException unanswered) {
            throw new IllegalStateException("The LINE push could not be completed.", unanswered);
        }
    }

    // 409: LINE already has this retry key. 429 and 5xx are transient; other 4xx are permanent.
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

    private record PushRequest(String to, List<FlexMessage> messages) { }
}
