package com.auvan.api.booking.service;

public interface PaymentProofStorage {
    void store(String objectKey, String contentType, byte[] content);

    byte[] load(String objectKey);
}
