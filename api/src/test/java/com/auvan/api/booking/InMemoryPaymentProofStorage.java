package com.auvan.api.booking;

import com.auvan.api.booking.service.PaymentProofStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
            throw new IllegalStateException("The payment-proof bucket is unreachable.");
        }
        return stored.content().clone();
    }

    public void failEveryStore() {
        failingStores = true;
    }

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
