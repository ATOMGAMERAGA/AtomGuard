package com.atomguard.web.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JWTAuthProviderTest {

    private JWTAuthProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JWTAuthProvider("test-secret-key-for-jwt-testing", 120);
    }

    @Test
    void should_generate_valid_token() {
        String token = provider.generateToken("admin");
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void should_validate_own_token() {
        String token = provider.generateToken("admin");
        JWTAuthProvider.JWTClaims claims = provider.validateToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.subject()).isEqualTo("admin");
        assertThat(claims.expiresAt()).isGreaterThan(claims.issuedAt());
    }

    @Test
    void should_reject_null_token() {
        assertThat(provider.validateToken(null)).isNull();
    }

    @Test
    void should_reject_empty_token() {
        assertThat(provider.validateToken("")).isNull();
    }

    @Test
    void should_reject_malformed_token() {
        assertThat(provider.validateToken("not.a.valid.jwt")).isNull();
        assertThat(provider.validateToken("onlyonepart")).isNull();
    }

    @Test
    void should_reject_tampered_token() {
        String token = provider.generateToken("admin");
        // Tamper with payload
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x" + "." + parts[2];
        assertThat(provider.validateToken(tampered)).isNull();
    }

    @Test
    void should_reject_token_with_wrong_secret() {
        String token = provider.generateToken("admin");
        JWTAuthProvider otherProvider = new JWTAuthProvider("different-secret", 120);
        assertThat(otherProvider.validateToken(token)).isNull();
    }

    @Test
    void should_preserve_username_with_special_chars() {
        String token = provider.generateToken("test_user");
        JWTAuthProvider.JWTClaims claims = provider.validateToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.subject()).isEqualTo("test_user");
    }

    @Test
    void should_have_correct_expiry() {
        JWTAuthProvider shortProvider = new JWTAuthProvider("secret", 5);
        assertThat(shortProvider.getExpiryMinutes()).isEqualTo(5);
    }

    @Test
    void should_set_expiry_in_future() {
        String token = provider.generateToken("admin");
        JWTAuthProvider.JWTClaims claims = provider.validateToken(token);
        assertThat(claims).isNotNull();
        long nowSeconds = System.currentTimeMillis() / 1000;
        // Token should expire ~120 minutes from now (with some tolerance)
        assertThat(claims.expiresAt()).isGreaterThan(nowSeconds + 7000);
        assertThat(claims.expiresAt()).isLessThan(nowSeconds + 7300);
    }
}
