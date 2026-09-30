package com.auvan.api.auth.client;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.exception.InvalidLineTokenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class LineTokenVerifierImpl implements LineTokenVerifier {
    private final RestClient lineClient;
    private final AuthProperties properties;

    @Autowired
    LineTokenVerifierImpl(AuthProperties properties) {
        this(properties, RestClient.builder());
    }

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

            // The configured host may be a test double (ADR-013); these iss, aud and
            // sub checks are what stop it vouching for a forged token.
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
