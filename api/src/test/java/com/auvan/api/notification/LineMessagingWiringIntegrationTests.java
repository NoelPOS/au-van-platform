package com.auvan.api.notification;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.notification.client.LineMessageSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deployed configuration, booted.
 *
 * <p>Every other test in this suite runs with {@code notification.line.enabled}
 * false, so none of them constructs the real sender and none of them would
 * notice a bean it depends on being absent — which is precisely the failure the
 * container check caught and the unit tests could not, because they build
 * {@code LineMessageSenderImpl} by hand. This class turns the property on in
 * isolation and asserts only that the context comes up with a sender in it,
 * exactly as {@code OutboxSchedulingIntegrationTests} does for the poller.
 *
 * <p>Nothing is sent: the token is blank, so a send would throw before reaching
 * the network, and nothing here calls one.
 */
@SpringBootTest(properties = {
        "notification.line.enabled=true",
        "notification.line.api-base-url=https://api.line.me",
        "notification.line.channel-access-token="
})
class LineMessagingWiringIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private ApplicationContext context;

    @Test
    void theApplicationStartsWithTheRealSenderWiredAndNoCredentialConfigured() {
        assertThat(context.getBeanNamesForType(LineMessageSender.class)).hasSize(1);
    }
}
