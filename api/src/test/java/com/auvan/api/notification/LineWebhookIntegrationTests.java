package com.auvan.api.notification;

import com.auvan.api.AuthenticationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LineWebhookIntegrationTests extends AuthenticationTestSupport {
    private static final String LINE_EVENT = """
            {"destination":"Uchannel","events":[{"type":"message","mode":"active",
            "source":{"type":"user","userId":"Ustudent"},"message":{"type":"text","text":"I need help from staff"}}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lineCanPostToTheWebhookWithoutSigningIn() throws Exception {
        mockMvc.perform(post("/api/line/webhook")
                        .header("x-line-signature", "anything")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LINE_EVENT))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void onlyPostIsOpenWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/api/line/webhook"))
                .andExpect(status().isUnauthorized());
    }
}
