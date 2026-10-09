package com.auvan.api.live;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class LiveUpdatesTestSupport extends AuthenticationTestSupport {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected AppUserRepository users;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    protected record SignedIn(UUID id, String accessToken) { }

    protected SignedIn student(String lineSubject) throws Exception {
        String token = signIn(lineSubject);
        return new SignedIn(users.findByLineSubject(lineSubject).orElseThrow().getId(), token);
    }

    protected SignedIn admin(String lineSubject) throws Exception {
        SignedIn signedIn = student(lineSubject);
        AppUser user = users.findByLineSubject(lineSubject).orElseThrow();
        user.promoteToAdmin();
        users.save(user);
        return new SignedIn(signedIn.id(), signIn(lineSubject));
    }

    protected String ticketFor(String accessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/events/ticket")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.ticket");
    }

    protected MockHttpServletResponse stream(SignedIn who) throws Exception {
        return mockMvc.perform(get("/api/v1/events").param("ticket", ticketFor(who.accessToken())))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    private String signIn(String lineSubject) throws Exception {
        String idToken = "id-token-" + lineSubject + "-" + UUID.randomUUID();
        when(lineTokenVerifier.verify(idToken)).thenReturn(new VerifiedLineIdentity(lineSubject, "Test User"));
        String response = mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
