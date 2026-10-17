package com.auvan.api.notification.service;

import com.auvan.api.notification.config.LineMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "notification.line", name = "enabled", havingValue = "true")
class RichMenuSync {
    // Bump this to ship a new menu: startup installs it when the default menu has another name.
    static final String MENU_NAME = "au-van-menu-v1";

    private static final Logger log = LoggerFactory.getLogger(RichMenuSync.class);

    private final LineMessagingProperties properties;
    private final RestClient api;
    private final RestClient dataApi;

    @Autowired
    RichMenuSync(LineMessagingProperties properties) {
        this(properties, RestClient.builder());
    }

    RichMenuSync(LineMessagingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        String authorization = "Bearer " + properties.channelAccessToken();
        this.api = builder.clone().baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorization).build();
        this.dataApi = builder.clone().baseUrl(properties.dataApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorization).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    void sync() {
        if (!properties.hasChannelAccessToken() || !properties.hasLiffUrl()) {
            return;
        }
        String step = "read the default rich menu";
        try {
            if (MENU_NAME.equals(defaultMenuName())) {
                log.info("LINE rich menu {} is already the default.", MENU_NAME);
                return;
            }
            step = "create the rich menu";
            String id = create();
            step = "upload the rich menu image";
            upload(id);
            step = "make the rich menu the default";
            makeDefault(id);
            step = "delete old rich menus";
            deleteAllExcept(id);
            log.info("LINE rich menu {} is installed as the default.", MENU_NAME);
        } catch (RestClientResponseException answered) {
            log.warn("Could not {}: LINE answered {}.", step, answered.getStatusCode().value());
        } catch (RuntimeException failed) {
            log.warn("Could not {}.", step, failed);
        }
    }

    private String defaultMenuName() {
        try {
            String id = api.get().uri("/v2/bot/user/all/richmenu").retrieve().body(MenuId.class).richMenuId();
            return api.get().uri("/v2/bot/richmenu/{id}", id).retrieve().body(Menu.class).name();
        } catch (HttpClientErrorException.NotFound noDefault) {
            return null;
        }
    }

    private String create() {
        RichMenu menu = RichMenu.of(MENU_NAME, trimSlash(properties.liffUrl()), trimSlash(properties.siteUrl()));
        return api.post().uri("/v2/bot/richmenu").contentType(MediaType.APPLICATION_JSON).body(menu)
                .retrieve().body(MenuId.class).richMenuId();
    }

    private void upload(String id) {
        dataApi.post().uri("/v2/bot/richmenu/{id}/content", id).contentType(MediaType.IMAGE_PNG).body(image())
                .retrieve().toBodilessEntity();
    }

    private void makeDefault(String id) {
        api.post().uri("/v2/bot/user/all/richmenu/{id}", id).retrieve().toBodilessEntity();
    }

    private void deleteAllExcept(String id) {
        List<Menu> menus = api.get().uri("/v2/bot/richmenu/list").retrieve().body(MenuList.class).richmenus();
        for (Menu menu : menus) {
            if (!menu.richMenuId().equals(id)) {
                api.delete().uri("/v2/bot/richmenu/{id}", menu.richMenuId()).retrieve().toBodilessEntity();
            }
        }
    }

    private static byte[] image() {
        try {
            return new ClassPathResource("line/rich-menu.png").getContentAsByteArray();
        } catch (IOException missing) {
            throw new UncheckedIOException(missing);
        }
    }

    private static String trimSlash(String url) {
        return url.replaceAll("/+$", "");
    }

    private record MenuId(String richMenuId) { }

    private record Menu(String richMenuId, String name) { }

    private record MenuList(List<Menu> richmenus) { }
}
