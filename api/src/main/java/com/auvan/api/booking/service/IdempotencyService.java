package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.IdempotencyKey;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {
    private final IdempotencyKeyRepository keys;
    private final ObjectMapper json;

    public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper json) {
        this.keys = keys;
        this.json = json;
    }

    public record StoredResponse(int status, String body) { }

    @Transactional(readOnly = true)
    public Optional<StoredResponse> find(UUID userId, String endpoint, String key, String requestHash) {
        return keys.findByUserIdAndEndpointAndIdempotencyKey(userId, endpoint, key).map(stored -> {
            if (!stored.getRequestHash().equals(requestHash)) {
                throw Problems.conflict("idempotency_key_reused",
                        "That Idempotency-Key was already used for a different request. Use a new key.");
            }
            return new StoredResponse(stored.getResponseStatus(), stored.getResponseBody());
        });
    }

    // Not @Transactional: joins the caller's transaction so the record commits with the booking.
    public StoredResponse record(UUID userId, String endpoint, String key, String requestHash, int status,
                                 Object response, OffsetDateTime now) {
        StoredResponse stored = new StoredResponse(status, json.writeValueAsString(response));
        try {
            keys.save(new IdempotencyKey(userId, endpoint, key, requestHash, stored.status(), stored.body(), now));
            // Flush inside the try: @UuidGenerator defers the insert past this catch.
            keys.flush();
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException duplicate) {
            // H2 may report this race as a lock failure rather than a constraint violation.
            throw Problems.conflict("idempotency_conflict",
                    "That request is already being processed. Please retry.", duplicate);
        }
        return stored;
    }

    public String fingerprint(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Every JVM is required to provide SHA-256.", impossible);
        }
    }
}
