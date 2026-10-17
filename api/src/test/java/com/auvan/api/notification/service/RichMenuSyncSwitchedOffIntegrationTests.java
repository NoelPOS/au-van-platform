package com.auvan.api.notification.service;

import com.auvan.api.AuthenticationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "notification.line.enabled=false",
        "notification.line.api-base-url=http://localhost:1",
        "notification.line.channel-access-token=configured-but-switched-off",
        "notification.line.liff-url=https://liff.line.me/1234-abcd"
})
class RichMenuSyncSwitchedOffIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private ApplicationContext context;

    @Test
    void switchingMessagingOffLeavesTheRichMenuAloneEvenWithATokenConfigured() {
        assertThat(context.getBeanNamesForType(RichMenuSync.class)).isEmpty();
    }
}
