package com.atomguard.velocity.module.geo;

/**
 * Ülke filtreleme kontrol sonucunu temsil eden değer nesnesi.
 * İzin verilen veya reddedilen bağlantılar için sebep bilgisi içerir.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class CountryFilterResult {
    private final boolean allowed;
    private final String reason;

    private CountryFilterResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static CountryFilterResult allow() {
        return new CountryFilterResult(true, null);
    }

    public static CountryFilterResult deny(String reason) {
        return new CountryFilterResult(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}