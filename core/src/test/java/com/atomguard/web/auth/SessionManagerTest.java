package com.atomguard.web.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        JWTAuthProvider provider = new JWTAuthProvider("session-test-secret-key", 120);
        sessionManager = new SessionManager(provider);
    }

    @Test
    void should_create_valid_session() {
        String token = sessionManager.createSession("admin");
        assertThat(token).isNotNull();
        assertThat(sessionManager.isValidSession(token)).isTrue();
    }

    @Test
    void should_return_username() {
        String token = sessionManager.createSession("admin");
        assertThat(sessionManager.getUsername(token)).isEqualTo("admin");
    }

    @Test
    void should_invalidate_session() {
        String token = sessionManager.createSession("admin");
        assertThat(sessionManager.isValidSession(token)).isTrue();
        sessionManager.invalidateSession(token);
        assertThat(sessionManager.isValidSession(token)).isFalse();
    }

    @Test
    void should_refresh_token() {
        String oldToken = sessionManager.createSession("admin");
        String newToken = sessionManager.refreshToken(oldToken);
        assertThat(newToken).isNotNull();
        // Old token should be blacklisted
        assertThat(sessionManager.isValidSession(oldToken)).isFalse();
        // New token should be valid
        assertThat(sessionManager.isValidSession(newToken)).isTrue();
    }

    @Test
    void should_reject_null_token() {
        assertThat(sessionManager.isValidSession(null)).isFalse();
        assertThat(sessionManager.getUsername(null)).isNull();
    }

    @Test
    void should_reject_invalid_token() {
        assertThat(sessionManager.isValidSession("invalid-token")).isFalse();
    }

    @Test
    void should_not_refresh_blacklisted_token() {
        String token = sessionManager.createSession("admin");
        sessionManager.invalidateSession(token);
        assertThat(sessionManager.refreshToken(token)).isNull();
    }

    @Test
    void should_track_blacklist_size() {
        assertThat(sessionManager.getBlacklistSize()).isEqualTo(0);
        String token = sessionManager.createSession("admin");
        sessionManager.invalidateSession(token);
        assertThat(sessionManager.getBlacklistSize()).isEqualTo(1);
    }

    @Test
    void should_cleanup_blacklist_when_too_large() {
        // Add many tokens to blacklist
        for (int i = 0; i < 100; i++) {
            sessionManager.invalidateSession("token-" + i);
        }
        assertThat(sessionManager.getBlacklistSize()).isEqualTo(100);
        sessionManager.cleanupBlacklist();
        // Should not clear since < 10000
        assertThat(sessionManager.getBlacklistSize()).isEqualTo(100);
    }

    @Test
    void should_return_null_username_for_blacklisted_token() {
        String token = sessionManager.createSession("admin");
        sessionManager.invalidateSession(token);
        assertThat(sessionManager.getUsername(token)).isNull();
    }
}
