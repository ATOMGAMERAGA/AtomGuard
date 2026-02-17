package com.atomguard.velocity.module.protocol;

import java.util.Set;

/**
 * Protokol sürümü filtresi - desteklenmeyen veya kötü niyetli protokolleri engeller.
 */
public class ProtocolVersionFilter {

    private static final Set<Integer> SUPPORTED_PROTOCOLS = Set.of(
        769, 768, 767, 766, 765, 764, 763, 762, 761, 760,  // 1.21.x
        759, 758, 757, 756, 755, 754, 753, 751, 736, 735   // 1.20.x - 1.17.x
    );

    private final boolean enforceKnown;
    private final int minProtocol;
    private final int maxProtocol;

    public ProtocolVersionFilter(boolean enforceKnown, int minProtocol, int maxProtocol) {
        this.enforceKnown = enforceKnown;
        this.minProtocol = minProtocol;
        this.maxProtocol = maxProtocol;
    }

    public FilterResult check(int protocol) {
        if (protocol < 0)
            return new FilterResult(false, "Negatif protokol sürümü: " + protocol);
        if (protocol < minProtocol)
            return new FilterResult(false, "Çok eski protokol: " + protocol);
        if (maxProtocol > 0 && protocol > maxProtocol)
            return new FilterResult(false, "Çok yeni protokol: " + protocol);
        if (enforceKnown && !SUPPORTED_PROTOCOLS.contains(protocol))
            return new FilterResult(false, "Bilinmeyen protokol: " + protocol);
        return new FilterResult(true, "ok");
    }

    public record FilterResult(boolean allowed, String reason) {}
}
