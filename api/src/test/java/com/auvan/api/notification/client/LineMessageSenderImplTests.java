package com.auvan.api.notification.client;

import com.auvan.api.notification.config.LineMessagingProperties;
import com.auvan.api.outbox.service.PermanentFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LineMessageSenderImplTests {
    private static final String TOKEN = "channel-access-token-for-this-test-only";
    private static final UUID ROW_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private MockRestServiceServer line;
    private LineMessageSenderImpl sender;

    private void withLine(String token) {
        RestClient.Builder builder = RestClient.builder();
        line = MockRestServiceServer.bindTo(builder).build();
        sender = new LineMessageSenderImpl(
                new LineMessagingProperties(token, "https://api.line.me", true), builder);
    }

    private static LinePushMessage message() {
        return new LinePushMessage("Ustudent-line", "AU-Van booking AUV-1\nYour seats are held.", ROW_ID.toString());
    }

    @Test
    void anAcceptedPushCarriesTheRecipientTheTextAndTheRowIdAsTheRetryKey() {
        withLine(TOKEN);
        line.expect(requestTo("https://api.line.me/v2/bot/message/push"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(header("X-Line-Retry-Key", ROW_ID.toString()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.to").value("Ustudent-line"))
                .andExpect(jsonPath("$.messages[0].type").value("text"))
                .andExpect(jsonPath("$.messages[0].text").value(message().text()))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();

        line.verify();
    }

    @Test
    void anAlreadyAcceptedRetryKeyIsSuccessBecauseTheStudentAlreadyHasTheMessage() {
        withLine(TOKEN);
        line.expect(requestTo("https://api.line.me/v2/bot/message/push"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .header("x-line-accepted-request-id", "the-original-request")
                        .body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();

        line.verify();
    }

    @Test
    void anUnknownRecipientIsPermanentSoTheRowCanDieOnTheFirstAttempt() {
        withLine(TOKEN);
        line.expect(requestTo("https://api.line.me/v2/bot/message/push"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"message\":\"The user hasn't added the LINE Official Account as a friend.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(PermanentFailureException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("added the LINE Official Account");
    }

    @Test
    void aMalformedRequestIsPermanentBecauseTheSameRequestIsSentAgainOnEveryRetry() {
        withLine(TOKEN);
        line.expect(requestTo("https://api.line.me/v2/bot/message/push"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"message\":\"Invalid reply token\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(message())).isInstanceOf(PermanentFailureException.class);
    }

    @Test
    void anUnauthorizedChannelIsPermanentBecauseNoRetryMintsAValidToken() {
        withLine(TOKEN);
        line.expect(requestTo("https://api.line.me/v2/bot/message/push"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(message())).isInstanceOf(PermanentFailureException.class);
    }

    @Test
    void aServerErrorIsTransientSoTheRowBacksOffAndIsTriedAgain() {
        withLine(TOKEN);
        line.expect(requestTo("https://api.line.me/v2/bot/message/push"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(PermanentFailureException.class)
                .hasMessageContaining("500");
    }

    @Test
    void aRateLimitIsTransientAlthoughItIsA4xx() {
        withLine(TOKEN);
        line.expect(requestTo("https://api.line.me/v2/bot/message/push"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(PermanentFailureException.class)
                .hasMessageContaining("429");
    }

    @Test
    void aBlankChannelAccessTokenFailsAtTheCallWithoutSendingAnythingAndWithoutBeingPermanent() {
        withLine("");

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(PermanentFailureException.class)
                .hasMessageContaining("LINE_CHANNEL_ACCESS_TOKEN");

        line.verify();
    }

    @Test
    void aRecipientWithNoLineUserIdIsPermanentAndIsNeverPutOnTheWire() {
        withLine(TOKEN);

        assertThatThrownBy(() -> sender.send(new LinePushMessage("  ", "Anything.", ROW_ID.toString())))
                .isInstanceOf(PermanentFailureException.class)
                .hasMessageContaining("no LINE user id");

        line.verify();
    }
}
