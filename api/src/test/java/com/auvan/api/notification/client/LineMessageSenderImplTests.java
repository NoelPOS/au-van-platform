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

/**
 * What the sender puts on the wire, and — the part that actually decides
 * whether a student is told anything — what it makes of the answer.
 *
 * <p>Against a {@link MockRestServiceServer}, so every status LINE documents is
 * exercised as a real response through the real client and no request ever
 * leaves the machine. Asserting the classification any other way would be
 * asserting about a {@code switch} rather than about the behaviour.
 */
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

    /**
     * The request itself, and the three things about it that have to be right:
     * the documented push endpoint, the channel access token as a bearer
     * credential, and the retry key in {@code X-Line-Retry-Key} — a hexadecimal
     * UUID, which is the format LINE requires and which the outbox row's own id
     * already is.
     */
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

    /**
     * 409 is LINE saying it has already accepted a request under this retry key,
     * which is the retry key doing precisely its job: an earlier attempt reached
     * the student even though this worker never saw the answer. Treat it as a
     * failure and a message that was in fact delivered is retried four more
     * times and then dead-lettered.
     */
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

    /**
     * The case trap 12 names: a student who has never added the official account
     * as a friend, and an unknown user id, are both 404 and both answer the same
     * way forever. A plain {@code RuntimeException} here would cost
     * {@code outbox.max-attempts} sends per notification for every such student.
     */
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

    /**
     * A server error is the one case LINE's own guidance names as safe to retry,
     * so it must not be a {@link PermanentFailureException} or a passing outage
     * would dead-letter every notification taken during it.
     */
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

    /**
     * The one place this departs from a literal reading of LINE's "do not retry
     * 4xx". A rate limit describes this moment rather than the request, and it
     * clears; dead-lettering a student's confirmation because the channel was
     * busy for a second would be wrong.
     */
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

    /**
     * {@code LineTokenVerifierImpl}'s precedent: a missing credential throws at
     * the call rather than at startup, so an instance still boots. It is
     * transient, because a token supplied afterwards makes the next retry work.
     * And nothing is sent — a push with an empty bearer would only be a 401 with
     * extra steps.
     */
    @Test
    void aBlankChannelAccessTokenFailsAtTheCallWithoutSendingAnythingAndWithoutBeingPermanent() {
        withLine("");

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(PermanentFailureException.class)
                .hasMessageContaining("LINE_CHANNEL_ACCESS_TOKEN");

        line.verify();
    }

    /** No {@code to}, no push, and no later attempt invents one. */
    @Test
    void aRecipientWithNoLineUserIdIsPermanentAndIsNeverPutOnTheWire() {
        withLine(TOKEN);

        assertThatThrownBy(() -> sender.send(new LinePushMessage("  ", "Anything.", ROW_ID.toString())))
                .isInstanceOf(PermanentFailureException.class)
                .hasMessageContaining("no LINE user id");

        line.verify();
    }
}
