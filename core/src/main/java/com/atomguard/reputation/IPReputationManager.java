package com.atomguard.reputation;

import com.atomguard.AtomGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.atomguard.api.IReputationService;

/**
 * Gelişmiş Çok Katmanlı IP İtibar ve Proxy/VPN Tespit Sistemi v2.4.0
 *
 * Katmanlar:
 *   1. Beyaz Liste (IP + Oyuncu) — anında geçiş
 *   2. Manuel Kara Liste — admin eklediği IP'ler
 *   3. Yerel Proxy Listesi — GitHub'dan indirilen devasa proxy IP seti
 *   4. CIDR Subnet Engelleme — bilinen datacenter/hosting IP aralıkları
 *   5. Bilinen Datacenter ASN Engelleme — hosting sağlayıcı ASN'leri
 *   6. ProxyCheck.io API — detaylı VPN/proxy analizi
 *   7. ip-api.com Yedek API — ProxyCheck.io başarısız olursa
 *
 * Özellikler:
 *   - Paralel proxy listesi indirme (CompletableFuture)
 *   - Otomatik periyodik yenileme
 *   - Retry mekanizması (üstel geri çekilme)
 *   - Kalıcı istatistik ve önbellek
 *   - Admin bildirimleri (in-game broadcast)
 *   - Saldırı modu entegrasyonu
 *   - Manuel IP ekleme/kaldırma komutları
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class IPReputationManager implements IReputationService {

    private final AtomGuard plugin;

    // ── Dosyalar ──
    private final File apiCacheFile;
    private final File proxyListFile;
    private final File manualBlocklistFile;
    private final File statsFile;
    private final Gson gson;

    // ── API Önbellek ──
    private Map<String, ReputationResult> apiCache;

    // ── IP Setleri ──
    private final Set<String> proxyIpSet = ConcurrentHashMap.newKeySet();
    private final Set<String> manualBlocklist = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelistedIps;
    private final Set<String> whitelistedPlayers;

    // ── CIDR Subnet Engelleme ──
    private final List<CidrRange> blockedCidrRanges = new CopyOnWriteArrayList<>();

    // ── Bilinen Datacenter ASN Engelleme ──
    private final Set<String> blockedAsns;

    // ── Proxy Liste URL'leri ──
    private final List<String> proxyListUrls;

    // ── Yapılandırma ──
    private final long cacheTtl;
    private final String primaryApiKey;
    private final boolean enabled;
    private final int riskThreshold;
    private final List<String> blockedCountries;
    private final boolean proxyListEnabled;
    private final boolean apiCheckEnabled;
    private final boolean backupApiEnabled;
    private final boolean adminNotifyEnabled;
    private final int downloadTimeoutMs;
    private final int maxRetries;
    private final boolean cidrBlockingEnabled;
    private final boolean asnBlockingEnabled;
    private final long autoRefreshTicks;

    // ── İstatistik ──
    private final AtomicInteger proxyListBlocks = new AtomicInteger(0);
    private final AtomicInteger apiBlocks = new AtomicInteger(0);
    private final AtomicInteger manualBlocks = new AtomicInteger(0);
    private final AtomicInteger cidrBlocks = new AtomicInteger(0);
    private final AtomicInteger whitelistPasses = new AtomicInteger(0);
    private final AtomicInteger totalChecks = new AtomicInteger(0);
    private final AtomicInteger totalBlocks = new AtomicInteger(0);
    private final AtomicLong lastRefreshTime = new AtomicLong(0);
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // ── Son Engellenen IP Geçmişi ──
    private final Deque<BlockRecord> recentBlocks = new ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT_BLOCKS = 50;

    // ── Scheduler Task ID ──
    private int autoRefreshTaskId = -1;

    public IPReputationManager(AtomGuard plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Dosya yolları
        this.apiCacheFile = new File(plugin.getDataFolder(), "ip_cache.json");
        this.proxyListFile = new File(plugin.getDataFolder(), "proxy_ips.txt");
        this.manualBlocklistFile = new File(plugin.getDataFolder(), "manual_blocklist.txt");
        this.statsFile = new File(plugin.getDataFolder(), "antivpn_stats.json");
        this.apiCache = new ConcurrentHashMap<>();

        // ── Yapılandırma Yükle ──
        this.enabled = plugin.getConfig().getBoolean("anti-vpn.enabled", false);
        this.cacheTtl = TimeUnit.HOURS.toMillis(plugin.getConfig().getLong("anti-vpn.cache-hours", 24));
        this.primaryApiKey = plugin.getConfig().getString("anti-vpn.api-kontrol.api-key", "");
        this.riskThreshold = plugin.getConfig().getInt("anti-vpn.risk-threshold", 60);
        this.blockedCountries = plugin.getConfig().getStringList("anti-vpn.blocked-countries");

        // Beyaz liste (IP)
        this.whitelistedIps = ConcurrentHashMap.newKeySet();
        whitelistedIps.addAll(plugin.getConfig().getStringList("anti-vpn.beyaz-liste"));
        whitelistedIps.add("127.0.0.1");
        whitelistedIps.add("0:0:0:0:0:0:0:1");

        // Beyaz liste (Oyuncu)
        this.whitelistedPlayers = ConcurrentHashMap.newKeySet();
        whitelistedPlayers.addAll(plugin.getConfig().getStringList("anti-vpn.oyuncu-beyaz-liste"));

        // Proxy liste ayarları
        this.proxyListEnabled = plugin.getConfig().getBoolean("anti-vpn.proxy-listesi.aktif", true);
        this.downloadTimeoutMs = plugin.getConfig().getInt("anti-vpn.proxy-listesi.indirme-timeout-ms", 30000);
        this.maxRetries = plugin.getConfig().getInt("anti-vpn.proxy-listesi.max-tekrar-deneme", 3);
        this.autoRefreshTicks = plugin.getConfig().getLong("anti-vpn.proxy-listesi.otomatik-yenileme-dakika", 0) * 60 * 20;

        // API ayarları
        this.apiCheckEnabled = plugin.getConfig().getBoolean("anti-vpn.api-kontrol.aktif", true);
        this.backupApiEnabled = plugin.getConfig().getBoolean("anti-vpn.api-kontrol.yedek-api-aktif", true);

        // CIDR engelleme
        this.cidrBlockingEnabled = plugin.getConfig().getBoolean("anti-vpn.cidr-engelleme.aktif", false);

        // ASN engelleme
        this.asnBlockingEnabled = plugin.getConfig().getBoolean("anti-vpn.asn-engelleme.aktif", false);
        this.blockedAsns = ConcurrentHashMap.newKeySet();
        blockedAsns.addAll(plugin.getConfig().getStringList("anti-vpn.asn-engelleme.engellenen-asnler"));

        // Admin bildirimleri
        this.adminNotifyEnabled = plugin.getConfig().getBoolean("anti-vpn.admin-bildirim", true);

        // Proxy liste URL'leri
        this.proxyListUrls = new ArrayList<>(plugin.getConfig().getStringList("anti-vpn.proxy-listesi.url-listesi"));
        if (proxyListUrls.isEmpty()) {
            proxyListUrls.add("https://raw.githubusercontent.com/TheSpeedX/PROXY-List/refs/heads/master/http.txt");
            proxyListUrls.add("https://raw.githubusercontent.com/TheSpeedX/PROXY-List/refs/heads/master/socks4.txt");
            proxyListUrls.add("https://raw.githubusercontent.com/TheSpeedX/PROXY-List/refs/heads/master/socks5.txt");
        }

        // ── CIDR Aralıklarını Yükle ──
        if (cidrBlockingEnabled) {
            loadCidrRanges();
        }

        // ── Verileri Yükle ──
        loadApiCache();
        loadManualBlocklist();
        loadStats();

        // ── Proxy Listesini Başlat ──
        if (enabled && proxyListEnabled) {
            initializeProxyList();
        }

        // ── Otomatik Yenileme Zamanlayıcısı ──
        if (enabled && proxyListEnabled && autoRefreshTicks > 0) {
            startAutoRefreshTask();
        }
    }

    // ═══════════════════════════════════════════════════
    //  CIDR Subnet Engelleme
    // ═══════════════════════════════════════════════════

    private void loadCidrRanges() {
        List<String> cidrList = plugin.getConfig().getStringList("anti-vpn.cidr-engelleme.engellenen-araliklar");
        for (String cidr : cidrList) {
            try {
                blockedCidrRanges.add(new CidrRange(cidr.trim()));
            } catch (Exception e) {
                plugin.getLogger().warning("[Anti-VPN] Geçersiz CIDR aralığı: " + cidr + " — " + e.getMessage());
            }
        }
        if (!blockedCidrRanges.isEmpty()) {
            plugin.getLogger().info("[Anti-VPN] " + blockedCidrRanges.size() + " CIDR aralığı yüklendi.");
        }
    }

    private boolean isInBlockedCidr(@NotNull String ip) {
        if (!cidrBlockingEnabled || blockedCidrRanges.isEmpty()) return false;
        try {
            InetAddress addr = InetAddress.getByName(ip);
            for (CidrRange range : blockedCidrRanges) {
                if (range.contains(addr)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ═══════════════════════════════════════════════════
    //  Proxy Liste Sistemi
    // ═══════════════════════════════════════════════════

    private void initializeProxyList() {
        // Önce yerel dosyadan yükle (hızlı başlangıç)
        if (proxyListFile.exists()) {
            loadProxyListFromFile();
            plugin.getLogger().info("[Anti-VPN] Yerel proxy listesinden " + proxyIpSet.size() + " IP yüklendi.");
        }
        // Arka planda güncel listeleri indir
        downloadAndMergeProxyLists();
    }

    private void loadProxyListFromFile() {
        try {
            List<String> lines = Files.readAllLines(proxyListFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    proxyIpSet.add(trimmed);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Anti-VPN] Yerel proxy listesi okunamadı: " + e.getMessage());
        }
    }

    /**
     * Tüm URL'lerden proxy listelerini PARALEL indirir, birleştirir ve yerel dosyaya kaydeder.
     */
    private void downloadAndMergeProxyLists() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            plugin.getLogger().info("[Anti-VPN] Zaten bir indirme işlemi devam ediyor.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            plugin.getLogger().info("[Anti-VPN] Proxy listeleri indiriliyor (" + proxyListUrls.size() + " kaynak)...");

            // Paralel indirme
            List<CompletableFuture<DownloadResult>> futures = new ArrayList<>();
            for (String listUrl : proxyListUrls) {
                futures.add(CompletableFuture.supplyAsync(() -> downloadWithRetry(listUrl)));
            }

            // Tüm indirmelerin tamamlanmasını bekle
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            Set<String> downloadedIps = ConcurrentHashMap.newKeySet();
            int successCount = 0;
            int totalDownloaded = 0;

            for (CompletableFuture<DownloadResult> future : futures) {
                try {
                    DownloadResult result = future.get();
                    if (result.success) {
                        downloadedIps.addAll(result.ips);
                        successCount++;
                        totalDownloaded += result.ips.size();
                        plugin.getLogger().info("[Anti-VPN]   " + result.sourceName + ": " + result.ips.size() + " IP");
                    } else {
                        plugin.getLogger().warning("[Anti-VPN]   " + result.sourceName + ": BAŞARISIZ — " + result.error);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[Anti-VPN] İndirme sonucu alınamadı: " + e.getMessage());
                }
            }

            if (!downloadedIps.isEmpty()) {
                int previousSize = proxyIpSet.size();
                proxyIpSet.clear();
                proxyIpSet.addAll(downloadedIps);
                saveProxyListToFile();
                lastRefreshTime.set(System.currentTimeMillis());

                long duration = System.currentTimeMillis() - startTime;
                String msg = String.format(
                        "[Anti-VPN] Proxy listeleri güncellendi! %d benzersiz IP (%d kaynaktan) — %dms — Önceki: %d",
                        proxyIpSet.size(), successCount, duration, previousSize
                );
                plugin.getLogger().info(msg);

                // Admin bildirimi
                if (adminNotifyEnabled) {
                    notifyAdmins("Anti-VPN proxy listesi güncellendi: " + proxyIpSet.size() + " IP yüklendi.");
                }
            } else if (successCount == 0) {
                plugin.getLogger().warning("[Anti-VPN] Hiçbir proxy listesi indirilemedi! Yerel önbellek kullanılıyor (" + proxyIpSet.size() + " IP).");
            }

            refreshInProgress.set(false);
        });
    }

    /**
     * Retry mekanizmalı indirme (üstel geri çekilme).
     */
    private DownloadResult downloadWithRetry(@NotNull String listUrl) {
        String sourceName = listUrl.substring(listUrl.lastIndexOf('/') + 1);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Set<String> ips = downloadProxyList(listUrl);
                return new DownloadResult(sourceName, ips, true, null);
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    // Üstel geri çekilme: 1s, 2s, 4s...
                    try {
                        Thread.sleep(1000L * (1L << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new DownloadResult(sourceName, Set.of(), false, "Interrupted");
                    }
                } else {
                    return new DownloadResult(sourceName, Set.of(), false, e.getMessage());
                }
            }
        }
        return new DownloadResult(sourceName, Set.of(), false, "Max retries exceeded");
    }

    private Set<String> downloadProxyList(@NotNull String listUrl) throws IOException {
        Set<String> ips = new HashSet<>();

        URL url = new URL(listUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "AtomGuard-AntiVPN/2.4.0");
        conn.setConnectTimeout(downloadTimeoutMs);
        conn.setReadTimeout(downloadTimeoutMs);
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                // Format: IP:PORT — sadece IP kısmını al
                String ip;
                int colonIndex = trimmed.lastIndexOf(':');
                if (colonIndex > 0) {
                    ip = trimmed.substring(0, colonIndex).trim();
                } else {
                    ip = trimmed;
                }

                if (isValidIpv4(ip)) {
                    ips.add(ip);
                }
            }
        } finally {
            conn.disconnect();
        }

        return ips;
    }

    private void saveProxyListToFile() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(proxyListFile, StandardCharsets.UTF_8))) {
                writer.write("# AtomGuard Proxy IP Listesi — v2.4.0");
                writer.newLine();
                writer.write("# Otomatik güncellendi: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                writer.newLine();
                writer.write("# Toplam: " + proxyIpSet.size() + " benzersiz IP");
                writer.newLine();
                writer.write("# Kaynaklar: " + proxyListUrls.size() + " URL");
                writer.newLine();
                writer.newLine();
                for (String ip : proxyIpSet) {
                    writer.write(ip);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Anti-VPN] Proxy listesi kaydedilemedi: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    //  Manuel Kara Liste
    // ═══════════════════════════════════════════════════

    private void loadManualBlocklist() {
        if (!manualBlocklistFile.exists()) return;
        try {
            List<String> lines = Files.readAllLines(manualBlocklistFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    manualBlocklist.add(trimmed);
                }
            }
            if (!manualBlocklist.isEmpty()) {
                plugin.getLogger().info("[Anti-VPN] Manuel kara listeden " + manualBlocklist.size() + " IP yüklendi.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Anti-VPN] Manuel kara liste okunamadı: " + e.getMessage());
        }
    }

    private void saveManualBlocklist() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(manualBlocklistFile, StandardCharsets.UTF_8))) {
                writer.write("# AtomGuard Manuel Kara Liste");
                writer.newLine();
                writer.write("# Elle eklenen IP adresleri — /atomguard proxylist add <ip> komutuyla eklenir");
                writer.newLine();
                writer.newLine();
                List<String> sorted = new ArrayList<>(manualBlocklist);
                Collections.sort(sorted);
                for (String ip : sorted) {
                    writer.write(ip);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[Anti-VPN] Manuel kara liste kaydedilemedi: " + e.getMessage());
        }
    }

    /**
     * Manuel kara listeye IP ekler.
     * @return true ise eklendi, false ise zaten vardı
     */
    public boolean addToManualBlocklist(@NotNull String ip) {
        boolean added = manualBlocklist.add(ip);
        if (added) {
            saveManualBlocklist();
            if (plugin.getRedisManager() != null) {
                plugin.getRedisManager().publish("IP_BLOCK", ip);
            }
        }
        return added;
    }

    /**
     * Manuel kara listeden IP kaldırır.
     * @return true ise kaldırıldı, false ise yoktu
     */
    public boolean removeFromManualBlocklist(@NotNull String ip) {
        boolean removed = manualBlocklist.remove(ip);
        if (removed) {
            saveManualBlocklist();
            if (plugin.getRedisManager() != null) {
                plugin.getRedisManager().publish("IP_UNBLOCK", ip);
            }
        }
        return removed;
    }

    /**
     * Beyaz listeye IP ekler.
     * @return true ise eklendi
     */
    public boolean addToWhitelist(@NotNull String ip) {
        return whitelistedIps.add(ip);
    }

    /**
     * Beyaz listeden IP kaldırır.
     * @return true ise kaldırıldı
     */
    public boolean removeFromWhitelist(@NotNull String ip) {
        return whitelistedIps.remove(ip);
    }

    // ═══════════════════════════════════════════════════
    //  IReputationService İmplementasyonu
    // ═══════════════════════════════════════════════════

    @Override
    public boolean isVPN(@NotNull String ipAddress) {
        // Hızlı kontrol (Senkron)
        CheckDetail detail = checkIpDetailed(ipAddress);
        return detail.isBlocked();
    }

    @Override
    public boolean isBlocked(@NotNull String ipAddress) {
        return manualBlocklist.contains(ipAddress) || (proxyListEnabled && proxyIpSet.contains(ipAddress));
    }

    @Override
    public void blockIP(@NotNull String ipAddress) {
        addToManualBlocklist(ipAddress);
    }

    @Override
    public void unblockIP(@NotNull String ipAddress) {
        removeFromManualBlocklist(ipAddress);
    }

    @Override
    public @NotNull Set<String> getBlockedIPs() {
        return Collections.unmodifiableSet(manualBlocklist);
    }

    @Override
    public boolean isWhitelisted(@NotNull String ipAddress) {
        return whitelistedIps.contains(ipAddress);
    }

    // ═══════════════════════════════════════════════════
    //  Çok Katmanlı IP Kontrol Sistemi
    // ═══════════════════════════════════════════════════

    /**
     * IP adresini 7 katmanlı kontrol sisteminden geçirir.
     *
     * Sıra:
     *   1. Beyaz liste (IP + Oyuncu) → Anında geçiş
     *   2. Manuel kara liste → Anında engel
     *   3. Yerel proxy listesi → Anında engel (O(1) HashSet)
     *   4. CIDR subnet kontrolü → Anında engel
     *   5. API önbellek → Anlık sonuç
     *   6. ProxyCheck.io API → Asenkron sorgu
     *   7. ip-api.com yedek API → ProxyCheck başarısız olursa
     */
    public CompletableFuture<ReputationResult> checkIp(@NotNull String ip, @Nullable String playerName) {
        totalChecks.incrementAndGet();

        if (!enabled) {
            return CompletableFuture.completedFuture(
                    ReputationResult.allowed("Sistem Devre Dışı"));
        }

        // Katman 1: Beyaz liste (IP)
        if (whitelistedIps.contains(ip)) {
            whitelistPasses.incrementAndGet();
            return CompletableFuture.completedFuture(ReputationResult.allowed("Beyaz Liste (IP)"));
        }

        // Katman 1b: Beyaz liste (Oyuncu)
        if (playerName != null && whitelistedPlayers.contains(playerName.toLowerCase())) {
            whitelistPasses.incrementAndGet();
            return CompletableFuture.completedFuture(ReputationResult.allowed("Beyaz Liste (Oyuncu)"));
        }

        // Katman 2: Manuel kara liste
        if (manualBlocklist.contains(ip)) {
            manualBlocks.incrementAndGet();
            totalBlocks.incrementAndGet();
            recordBlock(ip, playerName, "Manuel Kara Liste", 100);
            return CompletableFuture.completedFuture(
                    ReputationResult.blocked(100, "Manuel Kara Liste", "Bilinmiyor", "Bilinmiyor"));
        }

        // Katman 3: Yerel proxy listesi (O(1))
        if (proxyListEnabled && proxyIpSet.contains(ip)) {
            if (apiCheckEnabled) {
                // FP-01: Hemen engellemek yerine API ile doğrulamaya yönlendir (cross-validation)
                // API sorgusuna "proxy listesinde bulundu" sinyalini risk skoru olarak ekleyeceğiz
                return CompletableFuture.supplyAsync(() -> queryApis(ip, playerName, true));
            } else {
                proxyListBlocks.incrementAndGet();
                totalBlocks.incrementAndGet();
                recordBlock(ip, playerName, "Proxy Listesi", 100);
                return CompletableFuture.completedFuture(
                        ReputationResult.blocked(100, "Proxy Listesi", "Bilinmiyor", "Bilinmiyor"));
            }
        }

        // Katman 4: CIDR subnet kontrolü
        if (isInBlockedCidr(ip)) {
            cidrBlocks.incrementAndGet();
            totalBlocks.incrementAndGet();
            recordBlock(ip, playerName, "CIDR Engelleme", 100);
            return CompletableFuture.completedFuture(
                    ReputationResult.blocked(100, "Datacenter/Hosting CIDR", "Bilinmiyor", "Bilinmiyor"));
        }

        // Katman 5: API önbellek
        ReputationResult cached = apiCache.get(ip);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < cacheTtl)) {
            if (cached.isBlocked) totalBlocks.incrementAndGet();
            return CompletableFuture.completedFuture(cached);
        }

        // Katman 6-7: API kontrolü (asenkron)
        if (apiCheckEnabled) {
            return CompletableFuture.supplyAsync(() -> queryApis(ip, playerName, false));
        }

        return CompletableFuture.completedFuture(ReputationResult.allowed("API Devre Dışı"));
    }

    /**
     * Eski imza ile uyumluluk.
     */
    public CompletableFuture<ReputationResult> checkIp(@NotNull String ip) {
        return checkIp(ip, null);
    }

    /**
     * Bir IP'yi tüm katmanlarda senkron kontrol eder (komut için).
     */
    public CheckDetail checkIpDetailed(@NotNull String ip) {
        CheckDetail detail = new CheckDetail(ip);

        detail.whitelisted = whitelistedIps.contains(ip);
        detail.manualBlocked = manualBlocklist.contains(ip);
        detail.proxyListed = proxyListEnabled && proxyIpSet.contains(ip);
        detail.cidrBlocked = isInBlockedCidr(ip);

        ReputationResult cached = apiCache.get(ip);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < cacheTtl)) {
            detail.apiResult = cached;
        }

        return detail;
    }

    // ═══════════════════════════════════════════════════
    //  API Sorgu Katmanı
    // ═══════════════════════════════════════════════════

    private ReputationResult queryApis(@NotNull String ip, @Nullable String playerName, boolean wasInProxyList) {
        int baseRisk = wasInProxyList ? 40 : 0;

        // ProxyCheck.io (birincil)
        if (!primaryApiKey.isEmpty()) {
            ReputationResult result = queryProxyCheckApi(ip);
            if (result != null) {
                // FP-01: Proxy listesi riskini ekle
                int finalRisk = Math.min(100, result.riskScore + baseRisk);
                boolean blocked = result.isBlocked || finalRisk >= riskThreshold;
                String reason = result.type;
                if (!result.isBlocked && blocked) {
                    reason = "Proxy Listesi + API Risk (" + finalRisk + ")";
                }
                
                ReputationResult finalResult = new ReputationResult(blocked, finalRisk, reason, result.country, result.asn);

                // ASN engelleme kontrolü
                if (!finalResult.isBlocked && asnBlockingEnabled && blockedAsns.contains(finalResult.asn)) {
                    finalResult = ReputationResult.blocked(finalResult.riskScore, "Engellenen ASN: " + finalResult.asn, finalResult.country, finalResult.asn);
                }

                apiCache.put(ip, finalResult);
                if (finalResult.isBlocked) {
                    apiBlocks.incrementAndGet();
                    totalBlocks.incrementAndGet();
                    recordBlock(ip, playerName, finalResult.type, finalResult.riskScore);
                }
                return finalResult;
            }
        }

        // ip-api.com (yedek)
        if (backupApiEnabled) {
            ReputationResult result = queryIpApiBackup(ip, wasInProxyList);
            if (result != null) {
                apiCache.put(ip, result);
                if (result.isBlocked) {
                    apiBlocks.incrementAndGet();
                    totalBlocks.incrementAndGet();
                    recordBlock(ip, playerName, result.type, result.riskScore);
                }
                return result;
            }
        }

        if (wasInProxyList) {
            // API'lere ulaşılamadı ama proxy listesinde var -> Yumuşak engel (geciktir ama engelleme veya riskli say)
            // Mevcut mantıkta API kapalıyken direkt engelliyordu, şimdi risk=40 ile izin veriyoruz (eğer threshold > 40 ise)
            return ReputationResult.allowed("Proxy Listesinde (API Erişilemedi)");
        }

        return ReputationResult.allowed("API Sorgusu Başarısız");
    }

    @Nullable
    private ReputationResult queryProxyCheckApi(@NotNull String ip) {
        try {
            String apiUrl = "https://proxycheck.io/v2/" + ip + "?key=" + primaryApiKey + "&vpn=1&asn=1&risk=1";

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "AtomGuard-AntiVPN/2.4.0");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }

            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            conn.disconnect();

            if (json.has(ip)) {
                JsonObject ipData = json.getAsJsonObject(ip);
                String proxy = ipData.has("proxy") ? ipData.get("proxy").getAsString() : "no";
                int risk = ipData.has("risk") ? ipData.get("risk").getAsInt() : 0;
                String type = ipData.has("type") ? ipData.get("type").getAsString() : "Bilinmiyor";
                String country = ipData.has("isocode") ? ipData.get("isocode").getAsString() : "Bilinmiyor";
                String asn = ipData.has("asn") ? ipData.get("asn").getAsString() : "Bilinmiyor";

                boolean blocked = proxy.equalsIgnoreCase("yes")
                        || risk >= riskThreshold
                        || blockedCountries.contains(country);

                return new ReputationResult(blocked, risk, type, country, asn);
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.WARNING, "[Anti-VPN] ProxyCheck.io sorgusu başarısız: " + ip, e);
            }
        }
        return null;
    }

    @Nullable
    private ReputationResult queryIpApiBackup(@NotNull String ip, boolean wasInProxyList) {
        try {
            String apiUrl = "http://ip-api.com/json/" + ip + "?fields=status,proxy,hosting,isp,org,as,countryCode";

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "AtomGuard-AntiVPN/2.4.0");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }

            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            conn.disconnect();

            if (json.has("status") && json.get("status").getAsString().equals("success")) {
                boolean isProxy = json.has("proxy") && json.get("proxy").getAsBoolean();
                boolean isHosting = json.has("hosting") && json.get("hosting").getAsBoolean();
                String country = json.has("countryCode") ? json.get("countryCode").getAsString() : "Bilinmiyor";
                String asn = json.has("as") ? json.get("as").getAsString() : "Bilinmiyor";
                String org = json.has("org") ? json.get("org").getAsString() : "Bilinmiyor";

                int risk = wasInProxyList ? 40 : 0;
                String type = "Temiz";
                
                if (isProxy) { 
                    risk = Math.max(risk, 80); 
                    type = "Proxy (ip-api)"; 
                } else if (isHosting) { 
                    // FP-02: Hosting riskini 70'den 45'e düşür
                    risk = Math.max(risk, 45); 
                    type = "Hosting/DC (ip-api)"; 
                }

                boolean hostingEngelle = plugin.getConfig().getBoolean("anti-vpn.hosting-engelle", false);
                
                // FP-02: isHosting tek başına engelleme sebebi olmamalı (hosting-engelle kapalıysa)
                boolean blocked = isProxy 
                        || (isHosting && hostingEngelle && risk >= riskThreshold)
                        || (isHosting && wasInProxyList && (risk + 20) >= riskThreshold) // Hosting + Proxy Listesi kombinasyonu
                        || risk >= riskThreshold
                        || blockedCountries.contains(country);

                // ASN engelleme
                if (!blocked && asnBlockingEnabled) {
                    String asnNumber = asn.contains(" ") ? asn.split(" ")[0] : asn;
                    if (blockedAsns.contains(asnNumber)) {
                        blocked = true;
                        type = "Engellenen ASN: " + asnNumber;
                    }
                }

                return new ReputationResult(blocked, risk, type, country, org + " (" + asn + ")");
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.WARNING, "[Anti-VPN] ip-api.com sorgusu başarısız: " + ip, e);
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════
    //  Otomatik Yenileme
    // ═══════════════════════════════════════════════════

    private void startAutoRefreshTask() {
        autoRefreshTaskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (enabled && proxyListEnabled) {
                plugin.getLogger().info("[Anti-VPN] Otomatik proxy listesi yenilemesi başlatılıyor...");
                downloadAndMergeProxyLists();
            }
        }, autoRefreshTicks, autoRefreshTicks).getTaskId();

        long minutes = autoRefreshTicks / 20 / 60;
        plugin.getLogger().info("[Anti-VPN] Otomatik yenileme zamanlandı: her " + minutes + " dakikada bir.");
    }

    public void stopAutoRefreshTask() {
        if (autoRefreshTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(autoRefreshTaskId);
            autoRefreshTaskId = -1;
        }
    }

    // ═══════════════════════════════════════════════════
    //  Engelleme Kaydı ve Admin Bildirimi
    // ═══════════════════════════════════════════════════

    private void recordBlock(@NotNull String ip, @Nullable String playerName, @NotNull String reason, int risk) {
        BlockRecord record = new BlockRecord(ip, playerName, reason, risk, System.currentTimeMillis());
        recentBlocks.addFirst(record);
        while (recentBlocks.size() > MAX_RECENT_BLOCKS) {
            recentBlocks.removeLast();
        }
    }

    private void notifyAdmins(@NotNull String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("atomguard.admin")) {
                    player.sendMessage(net.kyori.adventure.text.Component.text()
                            .append(plugin.getMessageManager().parse("<gradient:#ff6b6b:#ffa500><bold>[Anti-VPN]</bold></gradient> "))
                            .append(net.kyori.adventure.text.Component.text(message, net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                            .build());
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════
    //  Önbellek ve İstatistik Yönetimi
    // ═══════════════════════════════════════════════════

    private void loadApiCache() {
        if (!apiCacheFile.exists()) return;
        try (Reader reader = new FileReader(apiCacheFile)) {
            Map<String, ReputationResult> loaded = gson.fromJson(reader,
                    new TypeToken<ConcurrentHashMap<String, ReputationResult>>() {}.getType());
            if (loaded != null) {
                long now = System.currentTimeMillis();
                loaded.entrySet().removeIf(entry -> (now - entry.getValue().timestamp) > cacheTtl);
                this.apiCache = new ConcurrentHashMap<>(loaded);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Anti-VPN] API önbelleği yüklenemedi: " + e.getMessage());
        }
    }

    private void loadStats() {
        if (!statsFile.exists()) return;
        try (Reader reader = new FileReader(statsFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("proxyListBlocks")) proxyListBlocks.set(json.get("proxyListBlocks").getAsInt());
            if (json.has("apiBlocks")) apiBlocks.set(json.get("apiBlocks").getAsInt());
            if (json.has("manualBlocks")) manualBlocks.set(json.get("manualBlocks").getAsInt());
            if (json.has("cidrBlocks")) cidrBlocks.set(json.get("cidrBlocks").getAsInt());
            if (json.has("totalChecks")) totalChecks.set(json.get("totalChecks").getAsInt());
            if (json.has("totalBlocks")) totalBlocks.set(json.get("totalBlocks").getAsInt());
            if (json.has("whitelistPasses")) whitelistPasses.set(json.get("whitelistPasses").getAsInt());
        } catch (Exception e) {
            plugin.getLogger().warning("[Anti-VPN] İstatistikler yüklenemedi: " + e.getMessage());
        }
    }

    public void saveCache() {
        // API önbellek
        try (Writer writer = new FileWriter(apiCacheFile)) {
            gson.toJson(apiCache, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("[Anti-VPN] API önbelleği kaydedilemedi: " + e.getMessage());
        }
        // Proxy listesi
        if (proxyListEnabled && !proxyIpSet.isEmpty()) {
            saveProxyListToFile();
        }
        // İstatistikler
        saveStats();
        // Manuel kara liste
        saveManualBlocklist();
    }

    private void saveStats() {
        try (Writer writer = new FileWriter(statsFile)) {
            JsonObject json = new JsonObject();
            json.addProperty("proxyListBlocks", proxyListBlocks.get());
            json.addProperty("apiBlocks", apiBlocks.get());
            json.addProperty("manualBlocks", manualBlocks.get());
            json.addProperty("cidrBlocks", cidrBlocks.get());
            json.addProperty("totalChecks", totalChecks.get());
            json.addProperty("totalBlocks", totalBlocks.get());
            json.addProperty("whitelistPasses", whitelistPasses.get());
            json.addProperty("lastSaved", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            gson.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("[Anti-VPN] İstatistikler kaydedilemedi: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    //  Getter'lar & Yardımcı Metodlar
    // ═══════════════════════════════════════════════════

    public void refreshProxyList() {
        if (!proxyListEnabled) return;
        downloadAndMergeProxyLists();
    }

    public boolean isEnabled()                { return enabled; }
    public int getProxyListSize()             { return proxyIpSet.size(); }
    public int getManualBlocklistSize()       { return manualBlocklist.size(); }
    public int getApiCacheSize()              { return apiCache.size(); }
    public int getProxyListBlockCount()       { return proxyListBlocks.get(); }
    public int getApiBlockCount()             { return apiBlocks.get(); }
    public int getManualBlockCount()          { return manualBlocks.get(); }
    public int getCidrBlockCount()            { return cidrBlocks.get(); }
    public int getWhitelistPassCount()        { return whitelistPasses.get(); }
    public int getTotalCheckCount()           { return totalChecks.get(); }
    public int getTotalBlockCount()           { return totalBlocks.get(); }
    public long getLastRefreshTime()          { return lastRefreshTime.get(); }
    public boolean isRefreshing()             { return refreshInProgress.get(); }
    public int getBlockedCidrCount()          { return blockedCidrRanges.size(); }
    public int getBlockedAsnCount()           { return blockedAsns.size(); }
    public int getWhitelistedIpCount()        { return whitelistedIps.size(); }
    public int getWhitelistedPlayerCount()    { return whitelistedPlayers.size(); }
    public List<BlockRecord> getRecentBlocks(){ return new ArrayList<>(recentBlocks); }

    public boolean isProxyListed(@NotNull String ip) {
        return proxyListEnabled && proxyIpSet.contains(ip);
    }

    public void shutdown() {
        stopAutoRefreshTask();
        saveCache();
    }

    // ═══════════════════════════════════════════════════
    //  Yardımcı Sınıflar
    // ═══════════════════════════════════════════════════

    private static boolean isValidIpv4(@NotNull String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── ReputationResult ──

    public static class ReputationResult {
        public final boolean isBlocked;
        public final int riskScore;
        public final String type;
        public final String country;
        public final String asn;
        public final long timestamp;

        public ReputationResult(boolean isBlocked, int riskScore, String type, String country, String asn) {
            this.isBlocked = isBlocked;
            this.riskScore = riskScore;
            this.type = type;
            this.country = country;
            this.asn = asn;
            this.timestamp = System.currentTimeMillis();
        }

        public static ReputationResult allowed(String type) {
            return new ReputationResult(false, 0, type, "Bilinmiyor", "Bilinmiyor");
        }

        public static ReputationResult blocked(int risk, String type, String country, String asn) {
            return new ReputationResult(true, risk, type, country, asn);
        }
    }

    // ── DownloadResult ──

    private static class DownloadResult {
        final String sourceName;
        final Set<String> ips;
        final boolean success;
        final String error;

        DownloadResult(String sourceName, Set<String> ips, boolean success, String error) {
            this.sourceName = sourceName;
            this.ips = ips;
            this.success = success;
            this.error = error;
        }
    }

    // ── BlockRecord ──

    public static class BlockRecord {
        public final String ip;
        public final String playerName;
        public final String reason;
        public final int riskScore;
        public final long timestamp;

        public BlockRecord(String ip, String playerName, String reason, int riskScore, long timestamp) {
            this.ip = ip;
            this.playerName = playerName != null ? playerName : "Bilinmiyor";
            this.reason = reason;
            this.riskScore = riskScore;
            this.timestamp = timestamp;
        }

        public String getTimeFormatted() {
            return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
        }
    }

    // ── CheckDetail (komut için detaylı kontrol sonucu) ──

    public static class CheckDetail {
        public final String ip;
        public boolean whitelisted;
        public boolean manualBlocked;
        public boolean proxyListed;
        public boolean cidrBlocked;
        public ReputationResult apiResult;

        public CheckDetail(String ip) {
            this.ip = ip;
        }

        public boolean isBlocked() {
            return manualBlocked || proxyListed || cidrBlocked
                    || (apiResult != null && apiResult.isBlocked);
        }
    }

    // ── CidrRange (CIDR Subnet) ──

    public static class CidrRange {
        private final byte[] network;
        private final int prefixLength;

        public CidrRange(@NotNull String cidr) throws Exception {
            String[] parts = cidr.split("/");
            if (parts.length != 2) throw new IllegalArgumentException("Geçersiz CIDR: " + cidr);
            this.network = InetAddress.getByName(parts[0]).getAddress();
            this.prefixLength = Integer.parseInt(parts[1]);
            if (prefixLength < 0 || prefixLength > 32) throw new IllegalArgumentException("Geçersiz prefix: " + prefixLength);
        }

        public boolean contains(@NotNull InetAddress address) {
            byte[] addr = address.getAddress();
            if (addr.length != network.length) return false;

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != network[i]) return false;
            }

            if (remainingBits > 0 && fullBytes < addr.length) {
                int mask = 0xFF << (8 - remainingBits);
                return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
            }

            return true;
        }
    }
}
