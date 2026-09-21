package com.auvan.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #41: {@code allowedOrigins} used to be hard-coded to
 * {@code http://localhost:5173} in {@code SecurityConfiguration} itself, with
 * no test exercising it at all. The Vite dev proxy makes local development
 * same-origin, so no CORS check ever ran in the flow every developer
 * exercises -- the fault was invisible until the web application was served
 * from anywhere else: a LIFF tunnel, a container, or the eventual CloudFront
 * distribution.
 *
 * <p>The registered CORS mapping is {@code /api/**} only (see
 * {@code SecurityConfiguration.corsConfigurationSource}), not
 * {@code /actuator/**}, so these tests target {@code /api/v1/trips}.
 * {@code CorsFilter} runs ahead of authorization in the filter chain, so a
 * request from an allowed origin still carries
 * {@code Access-Control-Allow-Origin} even though it goes on to fail
 * authentication -- CORS and authentication are independent gates, and the
 * bare {@code 401} confirms the request reached authorization at all rather
 * than being rejected earlier for some other reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void aConfiguredOriginIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/trips").header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void anUnconfiguredOriginIsRefused() throws Exception {
        // A CORS preflight, not a plain GET: a browser only sends one ahead of
        // a real cross-origin request, and it is the one place Spring's own
        // CorsFilter rejects an origin server-side rather than merely omitting
        // the Access-Control-Allow-Origin header and leaving the browser to
        // block the response. It runs ahead of the OPTIONS-permits-all
        // authorization rule, so this is decided by CORS, not by security.
        mockMvc.perform(options("/api/v1/trips")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
