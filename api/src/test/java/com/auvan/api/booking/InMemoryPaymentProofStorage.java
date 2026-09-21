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
    private volatile boolean failingStores;
    private volatile boolean failingLoads;

    @Override
    public void store(String objectKey, String contentType, byte[] content) {
        if (failingStores) {
            throw new IllegalStateException("The payment-proof bucket is unreachable.");
        }
        objects.put(objectKey, new StoredObject(contentType, content.clone()));
    }

    @Override
    public byte[] load(String objectKey) {
        StoredObject stored = objects.get(objectKey);
        if (failingLoads || stored == null) {
            // A missing object is a failure the caller converts, never a null:
            // the port says so, and S3 raises NoSuchKeyException for the same.
            throw new IllegalStateException("The payment-proof bucket is unreachable.");
        }
        return stored.content().clone();
    }

    /** Makes every later store throw, standing in for an unreachable bucket. */
    public void failEveryStore() {
        failingStores = true;
    }

    /** The same for reads, standing in for an object that is gone. */
    public void failEveryLoad() {
        failingLoads = true;
    }

    public void reset() {
        objects.clear();
        failingStores = false;
        failingLoads = false;
    }

    public Map<String, StoredObject> objects() {
        return Map.copyOf(objects);
    }
}
