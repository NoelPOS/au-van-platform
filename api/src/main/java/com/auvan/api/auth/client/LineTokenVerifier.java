package com.auvan.api.auth.client;

public interface LineTokenVerifier {
    VerifiedLineIdentity verify(String idToken);
}
