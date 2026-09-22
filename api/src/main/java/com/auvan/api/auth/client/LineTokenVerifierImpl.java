package com.auvan.api.auth.client;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.exception.InvalidLineTokenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifies a LIFF id token with LINE and says who presented it.
 *
 * <p>The host is {@code auth.line.api-base-url}, which defaults to
 * {@code https://api.line.me}; the client is therefore built in the constructor
 * rather than in a field initialiser, because the value is not known until the
 * properties are. This mirrors {@code LineMessageSenderImpl}, which takes
 * {@code notification.line.api-base-url} the same way and for the same reason,
 * and the second constructor exists for the same reason that class has one: a
 * test supplies a builder bound to a {@code MockRestServiceServer}.
 *
 * <p><strong>Configurable host, unchanged verification.</strong> Whatever the
 * host, the answer is accepted only when {@code iss} is
 * {@code https://access.line.me}, {@code aud} equals the configured channel id
 * and {@code sub} is non-blank. Nothing here trusts the caller's token; it
 * trusts the verification service's answer about it, so pointing the host away
 * from LINE is a deployment decision with real consequences and not a switch
 * that turns validation off (ADR-013).
 */
@Component
class LineTokenVerifierImpl implements LineTokenVerifier {
    private final RestClient lineClient;
    private final AuthProperties properties;

    @Autowired
    LineTokenVerifierImpl(AuthProperties properties) {
        this(properties, RestClient.builder());
    }

    /** Visible for tests, which pass a builder bound to a mock server. */
    LineTokenVerifierImpl(AuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.lineClient = builder.baseUrl(properties.line().apiBaseUrl()).build();
    }

    @Override
    public VerifiedLineIdentity verify(String idToken) {
        if (properties.line().channelId() == null || properties.line().channelId().isBlank()) {
            throw new IllegalStateException("LINE_CHANNEL_ID must be configured before live LIFF authentication is enabled.");
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("id_token", idToken);
        form.add("client_id", properties.line().channelId());

        try {
            var response = lineClient.post()
                    .uri("/oauth2/v2.1/verify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(LineVerificationResponse.class);

            if (response == null
                    || response.sub() == null || response.sub().isBlank()
                    || !properties.line().channelId().equals(response.aud())
                    || !"https://access.line.me".equals(response.iss())) {
                throw new InvalidLineTokenException();
            }

            return new VerifiedLineIdentity(response.sub(), response.name());
        } catch (RestClientException exception) {
            throw new InvalidLineTokenException();
        }
    }

    private record LineVerificationResponse(String iss, String sub, String aud, String name) { }
}
