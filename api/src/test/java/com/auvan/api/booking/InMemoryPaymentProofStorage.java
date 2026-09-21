package com.auvan.api.booking;

import com.auvan.api.booking.service.PaymentProofStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The port's second implementation, and the reason it is a port at all: the
 * submit path can be exercised, and made to fail, without a bucket anywhere.
 * {@code ./gradlew test} therefore needs no MinIO container and no credential,
 * which is the same posture the rest of this suite already has.
 */
public class InMemoryPaymentProofStorage implements PaymentProofStorage {
    public record StoredObject(String contentType, byte[] content) { }

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private volatile boolean failing;

    @Override
    public void store(String objectKey, String contentType, byte[] content) {
        if (failing) {
            throw new IllegalStateException("The payment-proof bucket is unreachable.");
        }
        objects.put(objectKey, new StoredObject(contentType, content.clone()));
    }

    /** Makes every later store throw, standing in for an unreachable bucket. */
    public void failEveryStore() {
        failing = true;
    }

    public void reset() {
        objects.clear();
        failing = false;
    }

    public Map<String, StoredObject> objects() {
        return Map.copyOf(objects);
    }
}
