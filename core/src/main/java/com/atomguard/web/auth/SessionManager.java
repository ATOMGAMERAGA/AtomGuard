package com.atomguard.web.auth;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT tabanli oturum yonetimi.
 * Token olusturma, dogrulama, yenileme ve kara liste yonetimi.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class SessionManager {

    /** Above this size, cleanupBlacklist() drops tokens whose JWT has expired. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final JWTAuthProvider jwtProvider;
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    public SessionManager(JWTAuthProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Yeni oturum olusturur.
     *
     * @param username Kullanici adi
     * @return JWT token
     */
    public String createSession(String username) {
        return jwtProvider.generateToken(username);
    }

    /**
     * Token'in gecerli olup olmadigini kontrol eder.
     *
     * @param token JWT token
     * @return Gecerliyse true
     */
    public boolean isValidSession(String token) {
        if (token == null || blacklistedTokens.contains(token)) return false;
        return jwtProvider.validateToken(token) != null;
    }

    /**
     * Token'dan kullanici adini cikarir.
     *
     * @param token JWT token
     * @return Kullanici adi veya null
     */
    public String getUsername(String token) {
        if (token == null || blacklistedTokens.contains(token)) return null;
        JWTAuthProvider.JWTClaims claims = jwtProvider.validateToken(token);
        return claims != null ? claims.subject() : null;
    }

    /**
     * Token'i kara listeye ekleyerek oturumu sonlandirir.
     *
     * @param token JWT token
     */
    public void invalidateSession(String token) {
        if (token != null) {
            blacklistedTokens.add(token);
        }
    }

    /**
     * Mevcut token gecerliyse yeni token olusturur.
     *
     * @param currentToken Mevcut JWT token
     * @return Yeni JWT token veya gecersizse null
     */
    public String refreshToken(String currentToken) {
        if (currentToken == null || blacklistedTokens.contains(currentToken)) return null;
        JWTAuthProvider.JWTClaims claims = jwtProvider.validateToken(currentToken);
        if (claims == null) return null;

        // Eski token'i kara listeye ekle
        blacklistedTokens.add(currentToken);

        // Yeni token olustur
        return jwtProvider.generateToken(claims.subject());
    }

    /**
     * Suresi dolmus token'lari kara listeden temizler.
     * Periyodik olarak cagrilmali.
     *
     * Lazy / threshold-based: only runs the per-token JWT validation pass
     * when the blacklist exceeds {@link #CLEANUP_THRESHOLD} entries.
     * Below that, this method is a no-op so the periodic timer doesn't
     * burn CPU re-validating thousands of tokens on every tick.
     */
    public void cleanupBlacklist() {
        if (blacklistedTokens.size() <= CLEANUP_THRESHOLD) {
            return;
        }
        blacklistedTokens.removeIf(token -> jwtProvider.validateToken(token) == null);
    }

    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }
}
