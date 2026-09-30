package com.auvan.api.notification;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.notification.client.LineMessageSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

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
