package com.auvan.api.live;

import com.auvan.api.auth.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveStreamAuthenticationIntegrationTests extends LiveUpdatesTestSupport {
    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private AuthProperties auth;

    private SignedIn student;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        student = student("Ustream-student");
    }

    @Test
    void aSignedInStudentOpensAnEventStreamWithATicket() throws Exception {
        var response = stream(student);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/event-stream");
        assertThat(response.getContentAsString()).contains(":connected");
    }

    @Test
    void aTicketIsOnlyIssuedToASignedInUser() throws Exception {
        mockMvc.perform(post("/api/v1/events/ticket")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/events/ticket").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theStreamRefusesAForgedTicket() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("ticket", "not-a-ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theStreamRefusesAnAccessTokenInPlaceOfATicket() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("ticket", student.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theStreamRefusesAnExpiredTicket() throws Exception {
        Instant issued = Instant.now().minusSeconds(600);
        String expired = sign(JwtClaimsSet.builder()
                .issuer(auth.jwt().issuer())
                .audience(List.of(auth.jwt().audience() + "-events"))
                .subject(student.id().toString())
                .issuedAt(issued)
                .expiresAt(issued.plusSeconds(60))
                .claim("role", "STUDENT")
                .build());

        mockMvc.perform(get("/api/v1/events").param("ticket", expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTicketIsNotAnAccessToken() throws Exception {
        String ticket = ticketFor(student.accessToken());

        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + ticket))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void onlyReadingTheStreamIsLeftToTheTicket() throws Exception {
        mockMvc.perform(post("/api/v1/events").param("ticket", ticketFor(student.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    private String sign(JwtClaimsSet claims) {
        var headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
