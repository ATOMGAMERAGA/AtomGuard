package com.atomguard.velocity.module.protocol;

import java.util.regex.Pattern;

/**
 * Handshake paket temizleyici - enjeksiyon saldırılarına karşı.
 */
public class HandshakeSanitizer {

    private static final Pattern SAFE_HOSTNAME = Pattern.compile("^[a-zA-Z0-9._\\-]{1,253}$");
    private static final Pattern NULL_BYTES = Pattern.compile("\\x00");

    public SanitizeResult sanitizeHostname(String hostname) {
        if (hostname == null) return new SanitizeResult(false, null, "Null hostname");
        if (NULL_BYTES.matcher(hostname).find())
            return new SanitizeResult(false, null, "Null byte tespit edildi");
        if (hostname.length() > 255)
            return new SanitizeResult(false, null, "Hostname çok uzun: " + hostname.length());

        // FML/Forge suffix'ini temizle
        String cleaned = hostname.replaceAll("\\x00FML\\x00", "")
                                  .replaceAll("\\x00FML2\\x00", "");

        if (!SAFE_HOSTNAME.matcher(cleaned).matches())
            return new SanitizeResult(false, null, "Geçersiz hostname karakterleri");

        return new SanitizeResult(true, cleaned, "ok");
    }

    public boolean isValidUsername(String username) {
        if (username == null || username.isBlank()) return false;
        if (username.length() < 3 || username.length() > 16) return false;
        return username.matches("[a-zA-Z0-9_]+");
    }

    public record SanitizeResult(boolean valid, String sanitized, String reason) {}
}
