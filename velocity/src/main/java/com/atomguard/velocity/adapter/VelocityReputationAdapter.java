package com.atomguard.velocity.adapter;

import com.atomguard.api.IReputationService;
import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.firewall.FirewallModule;
import com.atomguard.velocity.module.antivpn.VPNDetectionModule;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class VelocityReputationAdapter implements IReputationService {
    private final AtomGuardVelocity plugin;
    private final FirewallModule firewall;
    private final VPNDetectionModule vpn;

    public VelocityReputationAdapter(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.firewall = plugin.getFirewallModule();
        this.vpn = plugin.getVpnModule();
    }

    @Override
    public boolean isVPN(@NotNull String ipAddress) {
        return vpn != null && vpn.isEnabled() && vpn.isVPN(ipAddress);
    }

    @Override
    public boolean isBlocked(@NotNull String ipAddress) {
        if (firewall == null || !firewall.isEnabled()) return false;
        FirewallModule.FirewallCheckResult result = firewall.check(ipAddress);
        return result.verdict() == FirewallModule.FirewallVerdict.DENY;
    }

    @Override
    public void blockIP(@NotNull String ipAddress) {
        if (firewall != null) {
            firewall.banIP(ipAddress, 3600000L, "API Block"); // Default 1 hour
        }
    }

    @Override
    public void unblockIP(@NotNull String ipAddress) {
        if (firewall != null) {
            firewall.unbanIP(ipAddress);
        }
    }

    @Override
    public @NotNull Set<String> getBlockedIPs() {
        if (firewall != null) {
            return firewall.getTempBanManager().getBannedIPs();
        }
        return Set.of();
    }

    @Override
    public boolean isWhitelisted(@NotNull String ipAddress) {
        return firewall != null && firewall.getWhitelistManager().isWhitelisted(ipAddress);
    }
}
