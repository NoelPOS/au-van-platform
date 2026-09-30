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

    @Test
    void theVerificationGoesToTheConfiguredHostAndYieldsTheVerifiedSubject() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.line.me", "Ustudent-subject", CHANNEL_ID);

        assertThat(verifier.verify("the-id-token"))
                .isEqualTo(new VerifiedLineIdentity("Ustudent-subject", "Somchai"));

        line.verify();
    }

    @Test
    void anIssuerThatIsNotLineIsRefusedEvenFromTheConfiguredHost() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.example.test", "Ustudent-subject", CHANNEL_ID);

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(InvalidLineTokenException.class);

        line.verify();
    }

    @Test
    void anAudienceThatIsNotTheConfiguredChannelIsRefusedEvenFromTheConfiguredHost() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.line.me", "Ustudent-subject", "9999999999");

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(InvalidLineTokenException.class);

        line.verify();
    }

    @Test
    void aBlankSubjectIsRefusedEvenFromTheConfiguredHost() {
        withVerificationHostAndChannel(DOUBLE, CHANNEL_ID);
        answeringWith("https://access.line.me", "  ", CHANNEL_ID);

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(InvalidLineTokenException.class);

        line.verify();
    }

    @Test
    void aBlankChannelIdFailsAtTheCallWithoutSendingAnything() {
        withVerificationHostAndChannel(DOUBLE, "  ");

        assertThatThrownBy(() -> verifier.verify("the-id-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINE_CHANNEL_ID");

        line.verify();
    }
}
