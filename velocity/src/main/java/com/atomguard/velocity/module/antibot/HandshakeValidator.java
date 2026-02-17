package com.atomguard.velocity.module.antibot;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Handshake paket doğrulama kontrolleri.
 */
public class HandshakeValidator {

    private static final Pattern VALID_HOSTNAME = Pattern.compile("^[a-zA-Z0-9._\\-]{1,253}$");
    private static final Set<Integer> KNOWN_PROTOCOLS = Set.of(
        766, 765, 764, 763, 762, 761, 760, 759, 758, 757, // 1.21.x - 1.20.x
        756, 755, 754, 753, 752, 751, 736, 735, 578, 477  // 1.17.x - 1.14.x
    );

    private final boolean enforceKnownProtocols;

    public HandshakeValidator(boolean enforceKnownProtocols) {
        this.enforceKnownProtocols = enforceKnownProtocols;
    }

    public ValidationResult validate(String hostname, int port, int protocolVersion, String username) {
        if (hostname == null || hostname.isBlank())
            return new ValidationResult(false, "Boş hostname");
        if (!VALID_HOSTNAME.matcher(hostname).matches())
            return new ValidationResult(false, "Geçersiz hostname formatı");
        if (port < 1 || port > 65535)
            return new ValidationResult(false, "Geçersiz port: " + port);
        if (enforceKnownProtocols && protocolVersion > 0 && !KNOWN_PROTOCOLS.contains(protocolVersion))
            return new ValidationResult(false, "Bilinmeyen protokol: " + protocolVersion);
        if (username != null) {
            if (username.length() < 3 || username.length() > 16)
                return new ValidationResult(false, "Geçersiz kullanıcı adı uzunluğu");
            if (!username.matches("[a-zA-Z0-9_]+"))
                return new ValidationResult(false, "Geçersiz kullanıcı adı karakterleri");
        }
        return new ValidationResult(true, "ok");
    }

    public record ValidationResult(boolean valid, String reason) {}
}
