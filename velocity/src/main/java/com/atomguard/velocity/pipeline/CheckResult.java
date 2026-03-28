package com.atomguard.velocity.pipeline;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

public record CheckResult(boolean denied, boolean pipelineComplete, @Nullable Component kickMessage, @Nullable String module, @Nullable String reason, Severity severity) {

    /**
     * Deny ciddiyeti.
     * <ul>
     *   <li>{@link #SOFT}   — Geçici durum (rate limit, throttle, trust score) — violation kaydetme</li>
     *   <li>{@link #MEDIUM} — Orta ihlal (DDoS, VPN, protocol) — hafif violation</li>
     *   <li>{@link #HARD}   — Ciddi ihlal (bot tespiti, firewall ban) — tam violation kaydet</li>
     * </ul>
     */
    public enum Severity { SOFT, MEDIUM, HARD }

    public static CheckResult allowed() {
        return new CheckResult(false, false, null, null, null, Severity.MEDIUM);
    }

    /**
     * Verified bypass — oyuncu daha önce limbo doğrulamasından geçmiş.
     * Pipeline'daki geri kalan tüm check'ler atlanır (Firewall hariç).
     */
    public static CheckResult verifiedBypass() {
        return new CheckResult(false, true, null, "verified-bypass", "previously-verified", Severity.SOFT);
    }

    /** Orta şiddetli deny (varsayılan). */
    public static CheckResult deny(Component msg, String module, String reason) {
        return new CheckResult(true, false, msg, module, reason, Severity.MEDIUM);
    }

    /** Geçici / soft deny — trust score düşürme, violation kaydetme. */
    public static CheckResult softDeny(Component msg, String module, String reason) {
        return new CheckResult(true, false, msg, module, reason, Severity.SOFT);
    }

    /** Ciddi deny — tam violation kaydı (bot tespiti, firewall ban). */
    public static CheckResult hardDeny(Component msg, String module, String reason) {
        return new CheckResult(true, false, msg, module, reason, Severity.HARD);
    }
}
