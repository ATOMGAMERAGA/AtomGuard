package com.atomguard.velocity.module.antivpn;

import com.atomguard.velocity.util.NetworkUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ip-api.com API entegrasyonu (ücretsiz, dakikada 45 istek).
 *
 * <p>Proxy vs hosting ayrımı:
 * <ul>
 *   <li>{@code proxy=true} → kesin VPN/proxy</li>
 *   <li>{@code proxy=false &amp;&amp; hosting=true} → sadece datacenter IP (false positive riski YÜKSEK)</li>
 * </ul>
 *
 * <p>Eski {@code isVPN(ip)} metodu artık yalnızca {@code proxy=true} durumunda true döner.
 * {@code hosting=true} tek başına engelleme yapmaz.
 */
public class IPApiProvider {

    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0;
    private volatile long rateLimitResetMs = 0;

    /**
     * Geriye dönük uyumluluk metodu.
     * Artık yalnızca {@code proxy=true} ise true döner; {@code hosting=true} tek başına false döner.
     */
    public CompletableFuture<Boolean> isVPN(String ip) {
        return checkDetailed(ip).thenApply(DetailedResult::isProxy);
    }

    /**
     * Detaylı kontrol: proxy ve hosting flag'larını ayrı ayrı döner.
     *
     * @param ip kontrol edilecek IP
     * @return {@link DetailedResult} (isProxy, isHostingOnly, isp, org)
     */
    public CompletableFuture<DetailedResult> checkDetailed(String ip) {
        if (System.currentTimeMillis() < circuitOpenUntil) {
            return CompletableFuture.completedFuture(new DetailedResult(false, false, "", ""));
        }
        if (System.currentTimeMillis() < rateLimitResetMs) {
            return CompletableFuture.completedFuture(new DetailedResult(false, false, "", ""));
        }

        String url = "http://ip-api.com/json/" + ip + "?fields=proxy,hosting,isp,org";
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = NetworkUtils.httpGet(url, 5000);
                if (response == null) { recordFailure(); return new DetailedResult(false, false, "", ""); }
                failures.set(0);

                boolean proxy = response.contains("\"proxy\":true");
                boolean hosting = response.contains("\"hosting\":true");

                // ISP ve org bilgilerini çıkar (basit parse)
                String isp = extractStringField(response, "isp");
                String org = extractStringField(response, "org");

                // hosting=true ama proxy=false → hostingOnly (datacenter IP, false positive riski yüksek)
                boolean hostingOnly = !proxy && hosting;

                return new DetailedResult(proxy, hostingOnly, isp, org);
            } catch (Exception e) {
                recordFailure();
                return new DetailedResult(false, false, "", "");
            }
        });
    }

    private String extractStringField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return json.substring(start, end);
    }

    private void recordFailure() {
        if (failures.incrementAndGet() >= 3) {
            circuitOpenUntil = System.currentTimeMillis() + 60_000L;
            failures.set(0);
        }
    }

    public boolean isAvailable() { return System.currentTimeMillis() >= circuitOpenUntil; }

    /**
     * ip-api.com detaylı sonuç modeli.
     *
     * @param isProxy      proxy=true → kesin VPN/proxy
     * @param isHostingOnly proxy=false &amp;&amp; hosting=true → datacenter IP
     * @param isp          ISP adı
     * @param org          organizasyon adı
     */
    public record DetailedResult(boolean isProxy, boolean isHostingOnly, String isp, String org) {}
}
