package com.auvan.api.auth.client;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.exception.InvalidLineTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Where the verification request goes, and what the verifier will and will not
 * accept back from it.
 *
 * <p>{@code auth.line.api-base-url} was made configurable so the Playwright
 * suite can point an instance at a verification double and sign in with no LINE
 * channel (ADR-013). <strong>The point of this class is that moving the host is
 * the only thing that changed.</strong> Every rejection below is asserted
 * against a double that the verifier has been told to trust, so none of them
 * can be passing because the request failed to arrive: the double answers, and
 * the verifier refuses the answer anyway.
 *
 * <p>Against a {@link MockRestServiceServer} through the real
 * {@code RestClient}, as {@code LineMessageSenderImplTests} already does, so no
 * request ever leaves the machine.
 */
class LineTokenVerifierImplTests {
    private static final String CHANNEL_ID = "2000000000";
    private static final String DOUBLE = "http://line-verification.test";

    private MockRestServiceServer line;
    private LineTokenVerifierImpl verifier;

    private void withVerificationHostAndChannel(String baseUrl, String channelId) {
        RestClient.Builder builder = RestClient.builder();
        line = MockRestServiceServer.bindTo(builder).build();
        verifier = new LineTokenVerifierImpl(
                new AuthProperties(null, new AuthProperties.Line(channelId, baseUrl), null), builder);
    }

    private void answeringWith(String iss, String sub, String aud) {
        line.expect(requestTo(DOUBLE + "/oauth2/v2.1/verify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "id_token", "the-id-token",
                        "client_id", CHANNEL_ID)))
                .andRespond(withSuccess(
                        "{\"iss\":\"" + iss + "\",\"sub\":\"" + sub + "\",\"aud\":\"" + aud
                                + "\",\"name\":\"Somchai\"}",
                        MediaType.APPLICATION_JSON));
    }

    /**
     * The seam itself: the verification request goes to the configured host,
     * carrying the token and the channel id LINE's own endpoint documents.
     */
    @Test
    void theVerificationGoesToTheConfiguredHostAndYieldsTheVerifiedSubject() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.line.me", "Ustudent-subject", CHANNEL_ID);

        assertThat(verifier.verify("the-id-token"))
                .isEqualTo(new VerifiedLineIdentity("Ustudent-subject", "Somchai"));

        line.verify();
    }

    /**
     * The issuer check, which is the one that survives the host moving. A host
     * this instance has been pointed at is still not allowed to claim that
     * somebody else issued the token.
     */
    @Test
    void anIssuerThatIsNotLineIsRefusedEvenFromTheConfiguredHost() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.example.test", "Ustudent-subject", CHANNEL_ID);

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(InvalidLineTokenException.class);

        line.verify();
    }

    /** A token minted for another channel is another channel's, host or no host. */
    @Test
    void anAudienceThatIsNotTheConfiguredChannelIsRefusedEvenFromTheConfiguredHost() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.line.me", "Ustudent-subject", "9999999999");

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(InvalidLineTokenException.class);

        line.verify();
    }

    /**
     * A blank {@code sub} is the one that would be silent: {@code AuthService}
     * keys {@code app_users.line_subject} on it, so accepting it would put every
     * such caller on one row.
     */
    @Test
    void aBlankSubjectIsRefusedEvenFromTheConfiguredHost() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.line.me", "  ", CHANNEL_ID);

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(InvalidLineTokenException.class);

        line.verify();
    }

    /**
     * Unchanged behaviour, asserted here because the constructor moved: with no
     * channel id there is nothing to check {@code aud} against, and the failure
     * happens before anything is put on the wire rather than at startup, so an
     * instance that does not use LIFF still boots.
     */
    @Test
    void aBlankChannelIdFailsAtTheCallWithoutSendingAnything() {
        withVerificationHostAndChannel(DOUBLE, "  ");

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINE_CHANNEL_ID");

        line.verify();
    }
}
