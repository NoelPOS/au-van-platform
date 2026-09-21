package com.auvan.api.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How outbound work is retried, and whether this instance polls for it at all.
 *
 * <p>{@code maxAttempts} is five, the legacy application's own
 * {@code REMINDER_MAX_ATTEMPTS}. With {@code backoffBase} thirty seconds and
 * {@code backoffCap} one hour, five attempts span well under a day, which
 * matters because the retry key carried to LINE is only deduplicated for
 * twenty-four hours: a row still retrying after that could produce a second
 * message a student actually sees. Change any of the three and re-check that
 * arithmetic.
 *
 * <p>{@code lease} is how long a claim holds a row before it becomes due again.
 * It bounds how long a message waits after the worker holding it dies, so it is
 * short — but it must stay comfortably longer than one send takes, or a slow
 * send is handed to a second worker while the first is still in it.
 */
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(int batchSize, int maxAttempts, Duration backoffBase, Duration backoffCap,
                               Duration lease, Dispatch dispatch) {
    /**
     * The scheduled poller. {@code enabled} is {@code false} in the test
     * configuration: a live sweep running through every {@code @SpringBootTest}
     * would dispatch in the background while the booking and payment-proof
     * concurrency tests are mid-assertion.
     */
    public record Dispatch(boolean enabled, Duration pollInterval) { }
}
