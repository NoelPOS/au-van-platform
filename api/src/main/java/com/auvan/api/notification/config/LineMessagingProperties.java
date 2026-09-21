package com.auvan.api.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The LINE Messaging channel a push is sent through.
 *
 * <p><strong>A different channel from {@code auth.line.channel-id}.</strong>
 * That one is a LINE <em>Login</em> channel, used to verify a LIFF id token;
 * this one is a LINE <em>Messaging API</em> channel, used to push a message.
 * They are separate credentials on separate channels, and if the Messaging
 * channel is created under a different LINE provider than the Login channel,
 * the {@code sub} this system stores as {@code AppUser.lineSubject} is not a
 * valid push target at all — every send then fails with a recipient error that
 * looks exactly like a bug in this code. Nothing here can detect that; the
 * README says so next to the variable.
 *
 * <p>{@code enabled} decides whether an implementation of
 * {@code LineMessageSender} exists at all. With no implementation
 * {@code BookingNotificationHandler} records the omission and resolves the row,
 * which is the behaviour the outbox shipped with. It defaults to {@code true}
 * so that a deployment which forgets the token fails loudly and discoverably
 * rather than silently telling no one anything.
 */
@ConfigurationProperties(prefix = "notification.line")
public record LineMessagingProperties(String channelAccessToken, String apiBaseUrl, boolean enabled) {
    public boolean hasChannelAccessToken() {
        return channelAccessToken != null && !channelAccessToken.isBlank();
    }
}
