package com.auvan.api.notification.service;

import com.auvan.api.notification.config.LineMessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RichMenuSyncTests {
    private static final String TOKEN = "channel-access-token-for-this-test-only";
    private static final String API = "https://api.line.me/v2/bot";
    private static final String DATA_API = "https://api-data.line.me/v2/bot";
    private static final String LIFF = "https://liff.line.me/1234-abcd";

    private MockRestServiceServer line;
    private RichMenuSync sync;

    private void withLine(String token, String liffUrl) {
        RestClient.Builder builder = RestClient.builder();
        line = MockRestServiceServer.bindTo(builder).build();
        sync = new RichMenuSync(new LineMessagingProperties(token, "https://api.line.me", true, liffUrl,
                "https://api-data.line.me", "https://auvan.duckdns.org/"), builder);
    }

    private void expectDefaultMenu(String name) {
        line.expect(requestTo(API + "/user/all/richmenu"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(json("{\"richMenuId\":\"richmenu-old\"}"));
        line.expect(requestTo(API + "/richmenu/richmenu-old"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("{\"richMenuId\":\"richmenu-old\",\"name\":\"" + name
                        + "\",\"chatBarText\":\"Menu\",\"selected\":true,\"areas\":[]}"));
    }

    private void expectCreateUploadAndSetDefault() {
        line.expect(requestTo(API + "/richmenu"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.name").value(RichMenuSync.MENU_NAME))
                .andExpect(jsonPath("$.size.width").value(2500))
                .andExpect(jsonPath("$.chatBarText").value("Menu"))
                .andExpect(jsonPath("$.areas.length()").value(6))
                .andExpect(jsonPath("$.areas[0].action.uri").value(LIFF))
                .andExpect(jsonPath("$.areas[1].action.uri").value(LIFF + "/tickets"))
                .andExpect(jsonPath("$.areas[4].action.text").value("I need help from staff"))
                .andExpect(jsonPath("$.areas[5].action.uri").value("https://auvan.duckdns.org"))
                .andRespond(json("{\"richMenuId\":\"richmenu-new\"}"));
        line.expect(requestTo(DATA_API + "/richmenu/richmenu-new/content"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(image()))
                .andRespond(json("{}"));
        line.expect(requestTo(API + "/user/all/richmenu/richmenu-new"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(json("{}"));
    }

    private static byte[] image() {
        try {
            return new ClassPathResource("line/rich-menu.png").getContentAsByteArray();
        } catch (IOException missing) {
            throw new UncheckedIOException(missing);
        }
    }

    private static DefaultResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    @Test
    void aCurrentDefaultMenuIsLeftAloneWithoutCreatingAnything() {
        withLine(TOKEN, LIFF);
        expectDefaultMenu(RichMenuSync.MENU_NAME);

        sync.sync();

        line.verify();
    }

    @Test
    void withNoDefaultMenuTheNewOneIsInstalledAndEveryOtherMenuDeleted() {
        withLine(TOKEN, LIFF + "/");
        line.expect(requestTo(API + "/user/all/richmenu"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"no default rich menu\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        expectCreateUploadAndSetDefault();
        line.expect(requestTo(API + "/richmenu/list"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("{\"richmenus\":[{\"richMenuId\":\"richmenu-old\",\"name\":\"legacy\"},"
                        + "{\"richMenuId\":\"richmenu-new\",\"name\":\"" + RichMenuSync.MENU_NAME + "\"},"
                        + "{\"richMenuId\":\"richmenu-orphan\",\"name\":\"" + RichMenuSync.MENU_NAME + "\"}]}"));
        line.expect(requestTo(API + "/richmenu/richmenu-old"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(json("{}"));
        line.expect(requestTo(API + "/richmenu/richmenu-orphan"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(json("{}"));

        sync.sync();

        line.verify();
    }

    @Test
    void anOutdatedDefaultMenuIsReplaced() {
        withLine(TOKEN, LIFF);
        expectDefaultMenu("AU-Van main menu");
        expectCreateUploadAndSetDefault();
        line.expect(requestTo(API + "/richmenu/list"))
                .andRespond(json("{\"richmenus\":[{\"richMenuId\":\"richmenu-old\",\"name\":\"AU-Van main menu\"}]}"));
        line.expect(requestTo(API + "/richmenu/richmenu-old"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(json("{}"));

        sync.sync();

        line.verify();
    }

    @Test
    void whenSettingTheDefaultFailsNothingIsDeletedAndStartupGoesOn() {
        withLine(TOKEN, LIFF);
        expectDefaultMenu("AU-Van main menu");
        line.expect(requestTo(API + "/richmenu"))
                .andRespond(json("{\"richMenuId\":\"richmenu-new\"}"));
        line.expect(requestTo(DATA_API + "/richmenu/richmenu-new/content"))
                .andRespond(json("{}"));
        line.expect(requestTo(API + "/user/all/richmenu/richmenu-new"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> sync.sync()).doesNotThrowAnyException();

        line.verify();
    }

    @Test
    void anUnreachableLineDoesNotStopStartup() {
        withLine(TOKEN, LIFF);
        line.expect(requestTo(API + "/user/all/richmenu"))
                .andRespond(request -> { throw new IOException("connection refused"); });

        assertThatCode(() -> sync.sync()).doesNotThrowAnyException();

        line.verify();
    }

    @Test
    void withoutATokenLineIsNeverCalled() {
        withLine("", LIFF);

        sync.sync();

        line.verify();
    }

    @Test
    void withoutALiffUrlLineIsNeverCalled() {
        withLine(TOKEN, "");

        sync.sync();

        line.verify();
    }
}
