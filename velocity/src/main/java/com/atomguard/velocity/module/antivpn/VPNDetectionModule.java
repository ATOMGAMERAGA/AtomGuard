package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * VPN/Proxy tespit modülü — konsensüs tabanlı, doğrulanmış IP cache destekli.
 *
 * <p>Yeni özellikler:
 * <ul>
 *   <li>{@code verifiedCleanIPs} cache — temiz IP'lere bypass</li>
 *   <li>{@link #check(String, boolean)} — premium + verified bypass sonra konsensüs kontrolü</li>
 *   <li>{@link #markAsVerifiedClean(String)} — başarılı login sonrası çağrılır</li>
 *   <li>{@link DetectionResult} — isVPN, confidenceScore, reason, detectedBy, method</li>
 * </ul>
 */
public class VPNDetectionModule extends VelocityModule {

    private final VPNProviderChain providerChain;

    /** Daha önce temiz çıkmış IP'ler — max 10000 (LRU eviction) */
    private final Set<String> verifiedCleanIPs = ConcurrentHashMap.newKeySet(10000);
    private final Queue<String> verifiedCleanIPsOrder = new ConcurrentLinkedQueue<>();

    public VPNDetectionModule(AtomGuardVelocity plugin) {
        super(plugin, "vpn-proxy-engelleme");
        this.providerChain = new VPNProviderChain(plugin);
    }

    @Override
    public void onEnable() {
        logger.info("VPN Detection modülü etkinleştirildi (konsensüs sistemi).");
    }

    @Override
    public void onDisable() {
        providerChain.close();
        logger.info("VPN Detection modülü devre dışı bırakıldı.");
    }

    /**
     * Geriye dönük uyumluluk — isPremium=false varsayar.
     */
    public CompletableFuture<DetectionResult> check(String ip) {
        return check(ip, false);
    }

    /**
     * IP kontrolü: disabled → premium bypass → verified bypass → konsensüs kontrolü.
     *
     * @param ip        kontrol edilecek IP
     * @param isPremium premium hesap mı?
     * @return {@link DetectionResult}
     */
    public CompletableFuture<DetectionResult> check(String ip, boolean isPremium) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(new DetectionResult(false, 0, "disabled", List.of(), "disabled"));
        }

        // Premium bypass
        String premiumPolicy = getConfigString("premium-vpn-politikasi", "izin-ver");
        if (isPremium && "izin-ver".equalsIgnoreCase(premiumPolicy)) {
            return CompletableFuture.completedFuture(
                    new DetectionResult(false, 0, "Premium Bypass", List.of(), "premium-bypass"));
        }

        // Verified clean bypass
        if (verifiedCleanIPs.contains(ip)) {
            return CompletableFuture.completedFuture(
                    new DetectionResult(false, 0, "Verified Clean", List.of(), "verified-bypass"));
        }

        // Konsensüs tabanlı kontrol
        return providerChain.checkWithConsensus(ip).thenApply(vpnResult -> {
            if (vpnResult.isVPN()) {
                return new DetectionResult(true, vpnResult.getConfidenceScore(),
                        "VPN/Proxy Tespit Edildi: " + String.join(", ", vpnResult.getDetectedBy()),
                        vpnResult.getDetectedBy(), vpnResult.getMethod());
            }
            // Temiz çıktı → cache'e ekle
            markAsVerifiedClean(ip);
            return new DetectionResult(false, vpnResult.getConfidenceScore(),
                    "", vpnResult.getDetectedBy(), vpnResult.getMethod());
        });
    }

    /**
     * IP'yi doğrulanmış temiz olarak işaretle (başarılı login sonrası çağrılır).
     * LRU eviction: ekleme sırasındaki en eski IP çıkarılır.
     */
    public void markAsVerifiedClean(String ip) {
        if (verifiedCleanIPs.add(ip)) {
            verifiedCleanIPsOrder.offer(ip);
        }
        // Cache doluysa en eski IP'yi çıkar
        while (verifiedCleanIPs.size() > 10000) {
            String oldest = verifiedCleanIPsOrder.poll();
            if (oldest != null) verifiedCleanIPs.remove(oldest);
        }
    }

    /**
     * IP doğrulanmış temiz mi?
     */
    public boolean isVerifiedClean(String ip) {
        return verifiedCleanIPs.contains(ip);
    }

    /**
     * IP'nin doğrulanmış temiz durumunu iptal et (ihlal tespitinde kullanılır).
     */
    public void revokeVerifiedClean(String ip) {
        verifiedCleanIPs.remove(ip);
    }

    /**
     * VPN tespit sonucu modeli.
     */
    public static class DetectionResult {
        private final boolean isVPN;
        private final int confidenceScore;
        private final String reason;
        private final List<String> detectedBy;
        private final String method;

        public DetectionResult(boolean isVPN, int confidenceScore, String reason,
                                List<String> detectedBy, String method) {
            this.isVPN = isVPN;
            this.confidenceScore = confidenceScore;
            this.reason = reason;
            this.detectedBy = List.copyOf(detectedBy);
            this.method = method;
        }

        public boolean isVPN() { return isVPN; }
        public int getConfidenceScore() { return confidenceScore; }
        public String getReason() { return reason; }
        public List<String> getDetectedBy() { return detectedBy; }
        public String getMethod() { return method; }
    }
}
