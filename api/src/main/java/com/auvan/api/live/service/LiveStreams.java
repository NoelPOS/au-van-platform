package com.auvan.api.live.service;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.live.dto.LiveSignal;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@EnableScheduling
public class LiveStreams {
    private final Map<SseEmitter, LiveSubscriber> open = new ConcurrentHashMap<>();
    private final Duration lifetime;

    // A stream outlives no access token, so staying connected keeps needing a signed-in session.
    public LiveStreams(AuthProperties auth) {
        this.lifetime = auth.jwt().accessTokenTtl();
    }

    public SseEmitter open(LiveSubscriber subscriber) {
        SseEmitter emitter = new SseEmitter(lifetime.toMillis());
        open.put(emitter, subscriber);
        emitter.onCompletion(() -> open.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(failure -> open.remove(emitter));
        send(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    public void deliver(LiveSignal signal) {
        Visible visible = new Visible(signal.kind(), signal.id());
        open.forEach((emitter, subscriber) -> {
            if (subscriber.receives(signal)) {
                send(emitter, SseEmitter.event().data(visible));
            }
        });
    }

    @Scheduled(fixedRateString = "${live-updates.heartbeat-interval}")
    public void heartbeat() {
        open.keySet().forEach(emitter -> send(emitter, SseEmitter.event().comment("heartbeat")));
    }

    private record Visible(String kind, UUID id) { }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException gone) {
            open.remove(emitter);
        }
    }
}
