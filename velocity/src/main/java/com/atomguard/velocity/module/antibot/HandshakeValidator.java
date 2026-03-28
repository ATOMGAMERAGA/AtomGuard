package com.atomguard.velocity.module.antibot;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Handshake paket doğrulama kontrolleri.
 *
 * <p>Düzeltmeler (false positive önleme):
 * <ul>
 *   <li>{@link #sanitizeHostname} — Forge/FML null-byte marker'larını ve Velocity forwarding
 *       verilerini hostname'den temizler; aksi hâlde regex her zaman başarısız olurdu.</li>
 *   <li>Regex güncellendi: {@code :} karakterine izin verildi (IPv6 ve bazı SRV kayıtları).</li>
 *   <li>Daha geniş {@code KNOWN_PROTOCOLS} — 1.12.x ve 1.13.x protokolleri eklendi.</li>
 * </ul>
 */
public class HandshakeValidator {

    // : ve [ ] IPv6 formatı için eklendi; önceki regex Forge hostname'lerini reddediyordu
    private static final Pattern VALID_HOSTNAME = Pattern.compile("^[a-zA-Z0-9._:\\-\\[\\]]{1,253}$");

    private static final Set<Integer> KNOWN_PROTOCOLS = Set.of(
        769, 768, 767,                                     // 1.21.4, 1.21.3, 1.21.2
        766, 765, 764, 763, 762, 761, 760, 759, 758, 757, // 1.21.1 - 1.20.x
        756, 755, 754, 753, 752, 751, 736, 735, 578, 477, // 1.17.x - 1.14.x
        // 1.13.x — önceden eksikti
        404, 401, 393,
        // 1.12.x
        340, 338, 335, 315, 210,
        // 1.11.x - 1.8.x (yaygın cracked server sürümleri)
        315, 210, 110, 107, 47
    );

    private final boolean enforceKnownProtocols;
    /** Config'den okunan ek izinli protokoller (yeni MC sürümleri için) */
    private final Set<Integer> extraProtocols;

    public HandshakeValidator(boolean enforceKnownProtocols) {
        this(enforceKnownProtocols, Set.of());
    }

    public HandshakeValidator(boolean enforceKnownProtocols, Set<Integer> extraProtocols) {
        this.enforceKnownProtocols = enforceKnownProtocols;
        this.extraProtocols = extraProtocols != null ? extraProtocols : Set.of();
    }

    /**
     * Forge/FML null-byte marker'larını ve Velocity modern forwarding verilerini temizler.
     * <ul>
     *   <li>Forge/FML: {@code hostname\0FML\0} veya {@code hostname\0FML2\0} şeklinde gelir</li>
     *   <li>Velocity modern forwarding: hostname'e ek veri eklenebilir ({@code ///} ile ayrılmış)</li>
     * </ul>
     */
    private static String sanitizeHostname(String hostname) {
        if (hostname == null) return null;
        // Forge/FML null-byte marker
        int nullIdx = hostname.indexOf('\0');
        if (nullIdx > 0) {
            hostname = hostname.substring(0, nullIdx);
        }
        // Velocity modern forwarding verisi
        int fwdIdx = hostname.indexOf("///");
        if (fwdIdx > 0) {
            hostname = hostname.substring(0, fwdIdx);
        }
        return hostname.trim();
    }

    public ValidationResult validate(String hostname, int port, int protocolVersion, String username) {
        // Forge/FML ve Velocity forwarding marker'larını temizle
        hostname = sanitizeHostname(hostname);

        if (hostname == null || hostname.isBlank())
            return new ValidationResult(false, "Boş hostname");
        if (!VALID_HOSTNAME.matcher(hostname).matches())
            return new ValidationResult(false, "Geçersiz hostname formatı");
        if (port < 1 || port > 65535)
            return new ValidationResult(false, "Geçersiz port: " + port);
        if (enforceKnownProtocols && protocolVersion > 0
                && !KNOWN_PROTOCOLS.contains(protocolVersion)
                && !extraProtocols.contains(protocolVersion))
            return new ValidationResult(false, "Bilinmeyen protokol: " + protocolVersion);
        if (username != null) {
            // Bedrock/Floodgate prefix temizle (ör: ".BedrockPlayer" → "BedrockPlayer")
            // "." veya "*" ile başlayan isimler Floodgate'in standart prefix'leridir.
            String checkName = username;
            if (checkName.startsWith(".") || checkName.startsWith("*")) {
                checkName = checkName.substring(1);
            }
            if (checkName.length() < 3 || checkName.length() > 16)
                return new ValidationResult(false, "Geçersiz kullanıcı adı uzunluğu");
            if (!checkName.matches("[a-zA-Z0-9_]+"))
                return new ValidationResult(false, "Geçersiz kullanıcı adı karakterleri");
        }
        return new ValidationResult(true, "ok");
    }

    public record ValidationResult(boolean valid, String reason) {}
}
