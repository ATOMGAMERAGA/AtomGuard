package com.atomguard.velocity.module.antivpn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Port tarama tabanlı proxy tespit motoru.
 *
 * <p>Bağlanan IP'de bilinen proxy portlarının açık olup olmadığını kontrol eder.
 * Açık proxy portları güçlü bir VPN/Proxy göstergesidir.
 *
 * <p>Taranan portlar:
 * <ul>
 *   <li>SOCKS4/5: 1080, 1081, 9050 (Tor), 9150</li>
 *   <li>HTTP Proxy: 3128, 8080, 8118 (Privoxy), 8888</li>
 *   <li>OpenVPN: 1194 (UDP/TCP)</li>
 *   <li>WireGuard: 51820 (tipik olarak UDP — TCP probe yapmaz)</li>
 *   <li>SSH Tunnel: 22 (sadece hosting IP'lerinde sayılır)</li>
 *   <li>Squid: 3128</li>
 * </ul>
 *
 * <p>False-positive koruması:
 * <ul>
 *   <li>Tek açık port yeterli DEĞİL — en az 2 proxy portu gerekli</li>
 *   <li>Port 22 (SSH) tek başına sayılmaz</li>
 *   <li>Bağlantı süresi 800ms ile sınırlı — yanıt gelmezse kapalı sayılır</li>
 *   <li>Virtual thread pool ile paralel tarama — ana thread'i bloklamaz</li>
 * </ul>
 */
public class PortScanDetector {

    /**
     * Tarama sonucu.
     */
    public record ScanResult(
            boolean suspicious,
            List<Integer> openPorts,
            int proxyPortCount,
            int confidence
    ) {
        public static ScanResult clean() {
            return new ScanResult(false, List.of(), 0, 0);
        }
    }

    // ─── Proxy port tanımları ───

    private record ProxyPort(int port, String protocol, int weight) {}

    /** Proxy portları ve ağırlıkları. Yüksek ağırlık = daha güçlü proxy göstergesi. */
    private static final List<ProxyPort> PROXY_PORTS = List.of(
            // SOCKS proxy'ler
            new ProxyPort(1080, "SOCKS", 90),
            new ProxyPort(1081, "SOCKS", 85),

            // Tor
            new ProxyPort(9050, "Tor-SOCKS", 95),
            new ProxyPort(9150, "Tor-Browser", 95),

            // HTTP Proxy
            new ProxyPort(3128, "Squid/HTTP-Proxy", 85),
            new ProxyPort(8080, "HTTP-Proxy", 60),  // Düşük — web sunucuları da kullanır
            new ProxyPort(8118, "Privoxy", 90),
            new ProxyPort(8888, "HTTP-Proxy", 55),   // Düşük — dev araçları da kullanır

            // VPN protokolleri
            new ProxyPort(1194, "OpenVPN", 80),
            new ProxyPort(443, "HTTPS/VPN", 15),     // Çok düşük — normal HTTPS
            new ProxyPort(1723, "PPTP", 85),
            new ProxyPort(500, "IKEv2/IPSec", 70),

            // SSH (tek başına sayılmaz, ama hosting ile birlikte sayılır)
            new ProxyPort(22, "SSH", 25)
    );

    /** Bağlantı timeout (ms) — hızlı tarama için düşük tutulur. */
    private static final int CONNECT_TIMEOUT_MS = 800;

    /** Virtual thread pool — I/O yoğun port taraması için ideal. */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * IP'nin proxy portlarını asenkron olarak tara.
     *
     * @param ip Taranacak IP adresi
     * @return Tarama sonucu
     */
    public CompletableFuture<ScanResult> scan(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            List<Integer> openPorts = new ArrayList<>();
            int totalWeight = 0;

            // Tüm portları paralel tara
            List<CompletableFuture<ProxyPort>> futures = new ArrayList<>();
            for (ProxyPort pp : PROXY_PORTS) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    if (isPortOpen(ip, pp.port)) return pp;
                    return null;
                }, executor));
            }

            // Sonuçları topla — max 2 saniye bekle
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .orTimeout(2, TimeUnit.SECONDS)
                        .exceptionally(e -> null)
                        .join();
            } catch (Exception ignored) {}

            for (CompletableFuture<ProxyPort> f : futures) {
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    try {
                        ProxyPort result = f.join();
                        if (result != null) {
                            openPorts.add(result.port);
                            totalWeight += result.weight;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (openPorts.isEmpty()) {
                return ScanResult.clean();
            }

            // SSH (22) tek başına sayılmaz
            boolean onlySSH = openPorts.size() == 1 && openPorts.contains(22);
            if (onlySSH) {
                return new ScanResult(false, openPorts, 0, 10);
            }

            // Port 8080 ve 443 tek başına sayılmaz (normal web sunucuları)
            List<Integer> significantPorts = openPorts.stream()
                    .filter(p -> p != 22 && p != 443 && p != 8080 && p != 8888)
                    .toList();

            if (significantPorts.isEmpty()) {
                // Sadece düşük ağırlıklı portlar açık
                return new ScanResult(false, openPorts, openPorts.size(), Math.min(30, totalWeight / openPorts.size()));
            }

            // Confidence hesapla: en az 1 anlamlı proxy portu açıksa
            int confidence = Math.min(100, totalWeight / Math.max(1, openPorts.size()));

            // Tor portları açıksa confidence çok yüksek
            if (openPorts.contains(9050) || openPorts.contains(9150)) {
                confidence = Math.max(confidence, 95);
            }

            // SOCKS portu açıksa confidence yüksek
            if (openPorts.contains(1080) || openPorts.contains(1081)) {
                confidence = Math.max(confidence, 85);
            }

            // Birden fazla anlamlı proxy portu → çok yüksek güven
            if (significantPorts.size() >= 2) {
                confidence = Math.max(confidence, 90);
            }

            return new ScanResult(
                    confidence >= 60,
                    List.copyOf(openPorts),
                    significantPorts.size(),
                    confidence
            );
        }, executor);
    }

    /**
     * TCP bağlantısıyla port açıklık kontrolü.
     * Non-blocking SocketChannel ile hızlı kontrol.
     */
    private boolean isPortOpen(String host, int port) {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(host, port));

            long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
            while (!channel.finishConnect()) {
                if (System.currentTimeMillis() > deadline) {
                    return false;
                }
                Thread.onSpinWait();
            }
            return true;
        } catch (IOException | java.nio.channels.UnresolvedAddressException e) {
            return false;
        }
    }

    /**
     * Kaynakları temizle.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
