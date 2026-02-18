package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.concurrent.CompletableFuture;

public class VPNDetectionModule extends VelocityModule {

    private final VPNProviderChain providerChain;

    public VPNDetectionModule(AtomGuardVelocity plugin) {
        super(plugin, "vpn-proxy-engelleme");
        this.providerChain = new VPNProviderChain(plugin);
    }

    @Override
    public void onEnable() {
        logger.info("VPN Detection module enabled.");
    }

    @Override
    public void onDisable() {
        providerChain.close();
        logger.info("VPN Detection module disabled.");
    }
    
    public CompletableFuture<DetectionResult> check(String ip, boolean isPremium) {
        if (!isEnabled()) return CompletableFuture.completedFuture(new DetectionResult(false, 0, ""));
        
        // Premium Bypass Check
        String premiumPolicy = getConfigString("premium-vpn-politikasi", "izin-ver");
        if (isPremium && "izin-ver".equalsIgnoreCase(premiumPolicy)) {
             return CompletableFuture.completedFuture(new DetectionResult(false, 0, "Premium Bypass"));
        }

        return providerChain.check(ip).thenApply(isVpn -> {
            if (isVpn) {
                return new DetectionResult(true, 100, "VPN Detected");
            }
            return new DetectionResult(false, 0, "");
        });
    }

    public static class DetectionResult {
        private final boolean isVPN;
        private final int score;
        private final String reason;

        public DetectionResult(boolean isVPN, int score, String reason) {
            this.isVPN = isVPN;
            this.score = score;
            this.reason = reason;
        }

        public boolean isVPN() {
            return isVPN;
        }
    }
}
