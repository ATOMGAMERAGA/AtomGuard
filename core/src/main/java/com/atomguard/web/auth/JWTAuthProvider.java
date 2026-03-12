package com.atomguard.web.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HMAC-SHA256 tabanli JWT uretici ve dogrulayici.
 * Harici kutuphane kullanmaz.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class JWTAuthProvider {

    private static final String ALGORITHM = "HmacSHA256";
    private static final AtomicLong NONCE = new AtomicLong(0);
    private final byte[] secretBytes;
    private final int expiryMinutes;

    /**
     * JWT Claims kaydi.
     */
    public record JWTClaims(String subject, long issuedAt, long expiresAt) {}

    public JWTAuthProvider(String secret, int expiryMinutes) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expiryMinutes = expiryMinutes;
    }

    /**
     * Verilen kullanici adi icin JWT token olusturur.
     *
     * @param username Kullanici adi
     * @return JWT token string'i
     */
    public String generateToken(String username) {
        long now = System.currentTimeMillis() / 1000;
        long exp = now + (expiryMinutes * 60L);

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long nonce = NONCE.incrementAndGet();
        String payload = base64UrlEncode(
            "{\"sub\":\"" + escapeJson(username) + "\",\"iat\":" + now + ",\"exp\":" + exp + ",\"jti\":" + nonce + "}");

        String content = header + "." + payload;
        String signature = base64UrlEncode(hmacSha256(content));

        return content + "." + signature;
    }

    /**
     * JWT token'i dogrular ve claims dondurur.
     *
     * @param token JWT token
     * @return JWTClaims veya gecersizse null
     */
    public JWTClaims validateToken(String token) {
        if (token == null || token.isBlank()) return null;

        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        // Verify signature with constant-time comparison
        String content = parts[0] + "." + parts[1];
        byte[] expectedSig = hmacSha256(content);
        byte[] actualSig;
        try {
            actualSig = base64UrlDecode(parts[2]);
        } catch (Exception e) {
            return null;
        }

        if (!MessageDigest.isEqual(expectedSig, actualSig)) return null;

        // Decode payload
        String payloadJson;
        try {
            payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }

        // Simple JSON parsing (no library needed for our structured format)
        String sub = extractJsonString(payloadJson, "sub");
        long iat = extractJsonLong(payloadJson, "iat");
        long exp = extractJsonLong(payloadJson, "exp");

        if (sub == null || exp == 0) return null;

        // Check expiry
        long now = System.currentTimeMillis() / 1000;
        if (now > exp) return null;

        return new JWTClaims(sub, iat, exp);
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 hesaplama hatasi", e);
        }
    }

    private static String base64UrlEncode(String data) {
        return base64UrlEncode(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private static long extractJsonLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return 0;
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public int getExpiryMinutes() { return expiryMinutes; }
}
