package com.example.libraryseat.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtSigningKeyFactoryTest {

    @Test
    void fromSecret_plainTextWithHyphens_doesNotThrow() {
        assertNotNull(JwtSigningKeyFactory.fromSecret("library-seat-default-jwt-secret-key-32b"));
    }
}
