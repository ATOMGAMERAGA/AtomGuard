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
     */
    public void cleanupBlacklist() {
        // JWT token'lari kendi expire mekanizmasina sahip oldugundan,
        // blacklist'te biriken eski token'lari temizle
        // Basit yaklasim: blacklist boyutu limitini kontrol et
        if (blacklistedTokens.size() > 10000) {
            blacklistedTokens.clear();
        }
    }

    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }
}
