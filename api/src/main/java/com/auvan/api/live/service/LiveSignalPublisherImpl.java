package com.auvan.api.live.service;

import com.auvan.api.live.dto.LiveSignal;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@ConditionalOnProperty(prefix = "live-updates", name = "postgres-notify", havingValue = "true")
public class LiveSignalPublisherImpl implements LiveSignalPublisher, SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(LiveSignalPublisherImpl.class);
    private static final String CHANNEL = "live_updates";
    private static final int WAIT_MILLIS = 5_000;

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final LiveStreams streams;
    private volatile boolean running;

    public LiveSignalPublisherImpl(JdbcTemplate jdbc, DataSource dataSource, ObjectMapper json, LiveStreams streams) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.json = json;
        this.streams = streams;
    }

    // Runs on the caller's transaction: PostgreSQL delivers a NOTIFY only when that transaction commits.
    @Override
    public void publish(LiveSignal signal) {
        jdbc.queryForList("select pg_notify(?, ?)", CHANNEL, json.writeValueAsString(signal));
    }

    @Override
    public void start() {
        running = true;
        Thread.ofPlatform().daemon().name("live-updates-listener").start(this::listen);
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        while (running) {
            try (Connection connection = dataSource.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("listen " + CHANNEL);
                }
                PGConnection notifications = connection.unwrap(PGConnection.class);
                while (running) {
                    for (PGNotification notification : notifications.getNotifications(WAIT_MILLIS)) {
                        streams.deliver(json.readValue(notification.getParameter(), LiveSignal.class));
                    }
                }
            } catch (SQLException | RuntimeException failure) {
                if (running) {
                    log.warn("Live updates lost the database listener; reconnecting.", failure);
                    pause();
                }
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(WAIT_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
