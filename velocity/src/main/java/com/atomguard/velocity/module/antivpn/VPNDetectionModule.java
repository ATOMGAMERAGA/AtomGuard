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
 * Gelişmiş VPN/Proxy tespit modülü — v2.
 *
 * <h2>Mimari</h2>
 * <p>Çok katmanlı konsensüs tabanlı tespit sistemi. {@link VPNProviderChain}
 * üzerinden 10+ farklı tespit yöntemi kullanır:
 *
 * <ol>
 *   <li>Yerel IP listesi + CIDR aralıkları (kesin, anında)</li>
 *   <li>IP2Proxy offline veritabanı (çok yüksek doğruluk)</li>
 *   <li>DNSBL kara listeleri (güvenilir)</li>
 *   <li>ASN analizi — bilinen VPN/hosting/proxy ASN'leri</li>
 *   <li>Reverse DNS — hostname kalıp tespiti</li>
 *   <li>Port tarama — proxy portları kontrolü</li>
 *   <li>proxycheck.io, iphub.info, ip-api.com, abuseipdb API'leri</li>
 * </ol>
 *
 * <h2>False-Positive Koruması</h2>
 * <ul>
 *   <li>Tek sağlayıcı tek başına yeterli değil (yerel liste/CIDR hariç)</li>
 *   <li>Residential ISP beyaz listesi</li>
 *   <li>Hosting ASN'leri tek başına engelleme yapmaz</li>
 *   <li>Konsensüs eşiği: en az 2 güçlü pozitif oy</li>
 * </ul>
 *
 * <h2>Performans</h2>
 * <ul>
 *   <li>Virtual thread pool ile paralel sorgular</li>
 *   <li>50k IP önbellek (1 saat TTL)</li>
 *   <li>Yerel kontroller senkron, API kontrolleri asenkron</li>
 *   <li>5 saniye toplam timeout</li>
 *   <li>Aynı anda yüzlerce kontrolü kaldırabilir</li>
 * </ul>
 *
 * <p>Config anahtarı: {@code modules.vpn-proxy-block}
 */
public class VPNDetectionModule extends VelocityModule {

    private VPNProviderChain providerChain;

    /**
     * Daha önce doğrulanmış-temiz IP'ler.
     * Limbo'dan geçen veya API kontrolünden temiz çıkan IP'ler.
     * Max 20k giriş, LRU eviction.
     */
    private final Set<String> verifiedCleanIPs = ConcurrentHashMap.newKeySet();
    private final Queue<String> verifiedCleanIPsOrder = new ConcurrentLinkedQueue<>();
    private static final int MAX_VERIFIED_CLEAN = 20_000;

    public VPNDetectionModule(AtomGuardVelocity plugin) {
        super(plugin, "vpn-proxy-block");
    }

    @Override
    public int getPriority() { return 60; }

    @Override
    public void onEnable() {
        this.providerChain = new VPNProviderChain(plugin);

        logger.info("VPN/Proxy Detection v2 etkinleştirildi — çok katmanlı konsensüs sistemi.");
        logger.info("  Aktif katmanlar: yerel-liste, CIDR, IP2Proxy, DNSBL, ASN, rDNS, port-scan, API");

        // 15 dakikada bir cleanup
        plugin.getProxyServer().getScheduler()
                .buildTask(plugin, this::cleanup)
                .repeat(15, java.util.concurrent.TimeUnit.MINUTES)
                .schedule();

        // 1 saatte bir istatistik raporu
        plugin.getProxyServer().getScheduler()
                .buildTask(plugin, this::logStats)
                .repeat(60, java.util.concurrent.TimeUnit.MINUTES)
                .schedule();
    }

    @Override
    public void onDisable() {
        if (providerChain != null) providerChain.close();
        logger.info("VPN/Proxy Detection v2 devre dışı bırakıldı.");
    }

    // ─────────────────────────── Ana API ───────────────────────────

    /**
     * Geriye dönük uyumluluk — isPremium=false.
     */
    public CompletableFuture<DetectionResult> check(String ip) {
        return check(ip, false);
    }

    /**
     * IP kontrolü: disabled → premium bypass → verified bypass → çok katmanlı kontrol.
     *
     * @param ip        Kontrol edilecek IP
     * @param isPremium Premium hesap mı?
     * @return Tespit sonucu
     */
    public CompletableFuture<DetectionResult> check(String ip, boolean isPremium) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(
                    new DetectionResult(false, 0, "disabled", List.of(), "disabled"));
        }

        // Premium bypass
        String premiumPolicy = getConfigString("premium-vpn-policy", "allow");
        if (isPremium && "allow".equalsIgnoreCase(premiumPolicy)) {
            return CompletableFuture.completedFuture(
                    new DetectionResult(false, 0, "Premium Bypass", List.of(), "premium-bypass"));
        }

        // Verified clean bypass — daha önce temiz çıkmış IP
        if (verifiedCleanIPs.contains(ip)) {
            return CompletableFuture.completedFuture(
                    new DetectionResult(false, 0, "Verified Clean", List.of(), "verified-bypass"));
        }

        // Çok katmanlı konsensüs kontrolü
        return providerChain.checkWithConsensus(ip).thenApply(vpnResult -> {
            if (vpnResult.isVPN()) {
                return new DetectionResult(true, vpnResult.getConfidenceScore(),
                        "VPN/Proxy Tespit Edildi: " + String.join(", ", vpnResult.getDetectedBy()),
                        vpnResult.getDetectedBy(), vpnResult.getMethod());
            }

            // Temiz çıktı → verified clean cache'e ekle
            markAsVerifiedClean(ip);
            return new DetectionResult(false, vpnResult.getConfidenceScore(),
                    "", vpnResult.getDetectedBy(), vpnResult.getMethod());
        });
    }

    // ─────────────────────────── Verified Clean Cache ───────────────────────────

    /**
     * IP'yi doğrulanmış temiz olarak işaretle.
     * Başarılı login sonrası veya API kontrolünden temiz çıktığında çağrılır.
     */
    public void markAsVerifiedClean(String ip) {
        if (verifiedCleanIPs.add(ip)) {
            verifiedCleanIPsOrder.offer(ip);
        }
        // LRU eviction
        while (verifiedCleanIPs.size() > MAX_VERIFIED_CLEAN) {
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
     * IP'nin doğrulanmış temiz durumunu iptal et.
     * İhlal tespitinde kullanılır.
     */
    public void revokeVerifiedClean(String ip) {
        verifiedCleanIPs.remove(ip);
    }

    /**
     * Senkron VPN kontrolü (sadece cache/local).
     * Ağ sorgusu yapmaz.
     */
    public boolean isVPN(String ip) {
        if (!isEnabled()) return false;
        return providerChain != null && providerChain.isVPN(ip);
    }

    // ─────────────────────────── Yardımcı ───────────────────────────

    private void cleanup() {
        if (providerChain != null) providerChain.cleanup();
        // Çok eski verified clean IP'leri temizle
        while (verifiedCleanIPs.size() > MAX_VERIFIED_CLEAN) {
            String oldest = verifiedCleanIPsOrder.poll();
            if (oldest != null) verifiedCleanIPs.remove(oldest);
        }
    }

    private void logStats() {
        if (providerChain == null) return;
        logger.info("[VPN Stats] Kontrol={}, Engellenen={}, ÖnbellekHit={}, VerifiedClean={}",
                providerChain.getTotalChecks(),
                providerChain.getTotalBlocked(),
                providerChain.getCacheHits(),
                verifiedCleanIPs.size());
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
