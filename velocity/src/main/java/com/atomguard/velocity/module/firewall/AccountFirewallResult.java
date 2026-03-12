package com.atomguard.velocity.module.firewall;

/**
 * Hesap güvenlik duvarı kontrol sonucunu temsil eden değer nesnesi.
 * İzin verilen veya reddedilen bağlantılar için sebep bilgisi içerir.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class AccountFirewallResult {
    private final boolean allowed;
    private final String reason;

    private AccountFirewallResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static AccountFirewallResult allow() {
        return new AccountFirewallResult(true, null);
    }

    public static AccountFirewallResult deny(String reason) {
        return new AccountFirewallResult(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}