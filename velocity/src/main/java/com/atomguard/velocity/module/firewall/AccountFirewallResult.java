package com.atomguard.velocity.module.firewall;

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