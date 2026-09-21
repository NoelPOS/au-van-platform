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

/**
 * Remembers the response a critical write produced, so that a retry replays it
 * instead of writing again.
 *
 * <p>The response is kept as the bytes that were sent and replayed verbatim,
 * never rebuilt from the booking. Rebuilding would answer a retry with whatever
 * the booking looks like now, so a retry arriving after a cancellation would
 * return a cancelled booking under {@code 201 Created}.
 */
@Service
public class IdempotencyService {
    private final IdempotencyKeyRepository keys;
    private final ObjectMapper json;

    public IdempotencyService(IdempotencyKeyRepository keys, ObjectMapper json) {
        this.keys = keys;
        this.json = json;
    }

    /** A response already sent for one request, ready to be sent again unchanged. */
    public record StoredResponse(int status, String body) { }

    /**
     * The stored response for this key, or empty when the key is new.
     *
     * @throws org.springframework.web.server.ResponseStatusException 409 when the
     *         key was already used for a different payload
     */
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

    /**
     * Stores the response inside the caller's transaction, so that the record
     * and the booking it describes are committed together or not at all.
     *
     * <p>Deliberately not annotated {@code @Transactional}: this has to join
     * {@link BookingWriter}'s transaction rather than open one of its own.
     */
    public StoredResponse record(UUID userId, String endpoint, String key, String requestHash, int status,
                                 Object response, OffsetDateTime now) {
        StoredResponse stored = new StoredResponse(status, json.writeValueAsString(response));
        try {
            keys.save(new IdempotencyKey(userId, endpoint, key, requestHash, stored.status(), stored.body(), now));
            // @UuidGenerator is not an identity generator, so without this flush
            // the insert would defer to the commit — long after this catch block
            // has gone out of scope — and a duplicate key would become a 500.
            keys.flush();
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException duplicate) {
            // Depending on timing H2 reports contention on this row as a lock
            // failure rather than as a constraint violation. Both mean the same
            // thing here: someone else is writing this very key right now.
            throw Problems.conflict("idempotency_conflict",
                    "That request is already being processed. Please retry.", duplicate);
        }
        return stored;
    }

    /**
     * A fingerprint of the validated request rather than of the raw bytes, so
     * that two retries differing only in whitespace or field order are
     * recognised as the same request.
     */
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
