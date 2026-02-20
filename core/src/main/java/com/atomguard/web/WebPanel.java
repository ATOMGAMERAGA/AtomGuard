package com.atomguard.web;

import com.atomguard.AtomGuard;
import com.atomguard.manager.StatisticsManager;
import com.atomguard.module.AbstractModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;

/**
 * Lightweight embedded web panel for AtomGuard.
 * v2.3: HTTP Basic Auth, module management, event buffer, attack history.
 */
public class WebPanel {

    private final AtomGuard plugin;
    private HttpServer server;
    private final int port;
    private final boolean enabled;

    // Auth config
    private final boolean authEnabled;
    private final String authUser;
    private final String authPass;

    // Event buffer
    private final ConcurrentLinkedDeque<EventRecord> recentEvents = new ConcurrentLinkedDeque<>();
    private final int maxEvents;

    // Rate Limiting (Bounded LRU)
    private final java.util.Map<String, RateLimitTracker> rateLimits = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RateLimitTracker> eldest) {
            return size() > 2000;
        }
    });
    private final java.util.Map<String, Integer> loginAttempts = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 1000;
        }
    });
    private final java.util.Map<String, Long> validTokens = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.concurrent.ScheduledExecutorService tokenCleanupExecutor;

    public WebPanel(AtomGuard plugin) {
        this.plugin = plugin;
        this.port = plugin.getConfig().getInt("web-panel.port", 8080);
        this.enabled = plugin.getConfig().getBoolean("web-panel.enabled", false);
        this.authEnabled = plugin.getConfig().getBoolean("web-panel.kimlik-dogrulama.aktif", true);
        this.authUser = plugin.getConfig().getString("web-panel.kimlik-dogrulama.kullanici-adi", "admin");
        
        String pass = plugin.getConfig().getString("web-panel.kimlik-dogrulama.sifre", "atomguard2024");
        if ("atomguard2024".equals(pass) && enabled) {
            pass = generateRandomPassword();
            plugin.getConfig().set("web-panel.kimlik-dogrulama.sifre", pass);
            plugin.saveConfig();
            plugin.getLogger().warning("Web Panel için varsayılan şifre değiştirildi: " + pass);
        }
        this.authPass = pass;
        
        this.maxEvents = plugin.getConfig().getInt("web-panel.max-olay-sayisi", 100);
        
        // Cleanup task for expired tokens — executor kaydediliyor (stop()'ta kapatılacak)
        tokenCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        tokenCleanupExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            validTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        }, 1, 1, java.util.concurrent.TimeUnit.HOURS);
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        SecureRandom rnd = new SecureRandom();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public void start() {
        if (!enabled) return;

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new ProtectedHandler(new DashboardHandler()));
            server.createContext("/login", new LoginHandler());
            server.createContext("/modules", new ProtectedHandler(new ModulesPageHandler()));
            server.createContext("/api/stats", new ProtectedHandler(new StatsHandler()));
            server.createContext("/api/modules", new ProtectedHandler(new ModulesApiHandler()));
            server.createContext("/api/modules/toggle", new ProtectedHandler(new ModuleToggleHandler()));
            server.createContext("/api/events", new ProtectedHandler(new EventsApiHandler()));
            server.createContext("/api/attacks", new ProtectedHandler(new AttacksApiHandler()));
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            plugin.getLogger().info("Web Panel started on port " + port);
            plugin.getLogger().warning("UYARI: Web Panel HTTP üzerinden çalışıyor. Güvenlik için bir reverse proxy (Nginx, Caddy vb.) ile HTTPS kullanılması önerilir.");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start Web Panel: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════
    // Security Wrappers
    // ═══════════════════════════════════════

    private class ProtectedHandler implements HttpHandler {
        private final HttpHandler delegate;

        public ProtectedHandler(HttpHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS Headers — wildcard yerine config'den izin verilen origin
            String allowedOrigin = plugin.getConfig().getString("web-panel.cors-origin", "");
            String corsOrigin = (allowedOrigin == null || allowedOrigin.isBlank()) ? "null" : allowedOrigin;
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // 1. Rate Limiting
            if (!checkRateLimit(exchange)) {
                exchange.sendResponseHeaders(429, -1);
                exchange.close();
                return;
            }

            // 2. CSRF Protection for POST requests
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                String referer = exchange.getRequestHeaders().getFirst("Referer");
                String host = exchange.getRequestHeaders().getFirst("Host");

                boolean originOk = origin != null && (origin.contains(host) || origin.contains("localhost") || origin.contains("127.0.0.1"));
                boolean refererOk = referer != null && (referer.contains(host) || referer.contains("localhost") || referer.contains("127.0.0.1"));

                if (!originOk && !refererOk) {
                    sendResponse(exchange, 403, "application/json", "{\"error\":\"CSRF detected\"}");
                    return;
                }
            }

            // 3. Authentication (Token or Basic)
            if (!checkAuth(exchange)) {
                return;
            }

            delegate.handle(exchange);
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            int attempts;
            synchronized (loginAttempts) {
                attempts = loginAttempts.getOrDefault(ip, 0);
            }

            if (attempts > 5) {
                sendResponse(exchange, 429, "application/json", "{\"error\":\"Too many login attempts\"}");
                return;
            }

            String body = readBodyWithLimit(exchange, 1024);
            String user = null, pass = null;
            for (String param : body.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    if ("user".equals(kv[0])) user = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    if ("pass".equals(kv[0])) pass = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }

            if (authUser.equals(user) && authPass.equals(pass)) {
                String token = UUID.randomUUID().toString();
                validTokens.put(token, System.currentTimeMillis() + (2 * 3600 * 1000L)); // 2 hour expiry
                synchronized (loginAttempts) {
                    loginAttempts.remove(ip);
                }
                sendResponse(exchange, 200, "application/json", "{\"token\":\"" + token + "\"}");
            } else {
                synchronized (loginAttempts) {
                    loginAttempts.put(ip, attempts + 1);
                }
                sendResponse(exchange, 401, "application/json", "{\"error\":\"Invalid credentials\"}");
            }
        }
    }

    private boolean checkRateLimit(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        RateLimitTracker tracker;
        synchronized (rateLimits) {
            tracker = rateLimits.get(ip);
            if (tracker == null) {
                tracker = new RateLimitTracker();
                rateLimits.put(ip, tracker);
            }
        }
        return tracker.tryAcquire();
    }

    private static class RateLimitTracker {
        private final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger(0);
        private volatile long lastReset = System.currentTimeMillis();

        public boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - lastReset > 1000) { // 1 second window
                lastReset = now;
                requests.set(0);
            }
            return requests.incrementAndGet() <= 20; // 20 req/sec limit
        }
    }

    private String readBodyWithLimit(HttpExchange exchange, int limit) throws IOException {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            byte[] data = is.readNBytes(limit);
            if (is.read() != -1) {
                throw new IOException("Request body too large");
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (tokenCleanupExecutor != null && !tokenCleanupExecutor.isShutdown()) {
            tokenCleanupExecutor.shutdown();
        }
    }

    // ═══════════════════════════════════════
    // Event recording
    // ═══════════════════════════════════════

    /**
     * Records a blocked exploit event.
     */
    public void recordEvent(String moduleName, String playerName, String details) {
        EventRecord record = new EventRecord();
        record.moduleName = moduleName;
        record.playerName = playerName;
        record.details = details;
        record.timestamp = System.currentTimeMillis();
        recentEvents.addFirst(record);

        while (recentEvents.size() > maxEvents) {
            recentEvents.pollLast(); // removeLast() yerine pollLast() — boş deque'de NPE güvenli
        }
    }

    public List<EventRecord> getRecentEvents() {
        return new ArrayList<>(recentEvents);
    }

    // ═══════════════════════════════════════
    // Auth
    // ═══════════════════════════════════════

    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (!authEnabled) return true;

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"AtomGuard\"");
            exchange.sendResponseHeaders(401, -1);
            return false;
        }

        if (authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (isTokenValid(token)) return true;
            exchange.sendResponseHeaders(401, -1);
            return false;
        }

        if (authHeader.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            if (parts.length == 2 && parts[0].equals(authUser) && parts[1].equals(authPass)) {
                return true;
            }
        }

        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"AtomGuard\"");
        exchange.sendResponseHeaders(401, -1);
        return false;
    }

    private boolean isTokenValid(String token) {
        Long expiry = validTokens.get(token);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            validTokens.remove(token);
            return false;
        }
        return true;
    }

    private void sendResponse(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "")
                   .replace("\t", "    ");
    }

    private String formatTimestamp(long ts) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(ts));
    }

    // ═══════════════════════════════════════
    // Handlers
    // ═══════════════════════════════════════

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Auth checked by ProtectedHandler

            long blockedTotal = plugin.getModuleManager().getTotalBlockedCount();
            int onlineCount = Bukkit.getOnlinePlayers().size();
            boolean isAttack = plugin.getAttackModeManager().isAttackMode();
            int moduleCount = plugin.getModuleManager().getEnabledModuleCount();
            int totalModules = plugin.getModuleManager().getTotalModuleCount();

            // Module stats rows
            StringBuilder moduleRows = new StringBuilder();
            for (AbstractModule module : plugin.getModuleManager().getAllModules()) {
                String statusColor = module.isEnabled() ? "#00ff88" : "#ff4444";
                String statusText = module.isEnabled() ? "Aktif" : "Kapali";
                moduleRows.append(String.format(
                    "<tr><td>%s</td><td style='color:%s'>%s</td><td>%d</td></tr>",
                    escapeHtml(module.getName()), statusColor, statusText, module.getBlockedCount()));
            }

            // Recent events rows
            StringBuilder eventRows = new StringBuilder();
            List<EventRecord> events = getRecentEvents();
            int eventLimit = Math.min(events.size(), 20);
            for (int i = 0; i < eventLimit; i++) {
                EventRecord ev = events.get(i);
                eventRows.append(String.format(
                    "<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                    formatTimestamp(ev.timestamp), escapeHtml(ev.playerName),
                    escapeHtml(ev.moduleName), escapeHtml(ev.details)));
            }

            String html = "<!DOCTYPE html><html><head><title>AtomGuard Dashboard</title>"
                + "<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:'Segoe UI',sans-serif;background:#0a0a0a;color:#eee;margin:0;padding:20px;}"
                + ".header{text-align:center;margin-bottom:30px;}"
                + "h1{color:#00ff88;margin:0;font-size:2em;}"
                + ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:15px;margin-bottom:30px;}"
                + ".card{background:#1a1a2e;padding:20px;border-radius:12px;text-align:center;border:1px solid #333;}"
                + ".card h3{color:#888;margin:0 0 10px 0;font-size:0.9em;}"
                + ".card .value{font-size:2em;font-weight:bold;}"
                + ".attack{color:#ff4444;animation:pulse 1s infinite;}"
                + ".normal{color:#00ff88;}"
                + "@keyframes pulse{0%,100%{opacity:1;}50%{opacity:0.5;}}"
                + "table{width:100%;border-collapse:collapse;margin-top:10px;}"
                + "th,td{padding:8px 12px;text-align:left;border-bottom:1px solid #333;}"
                + "th{color:#00ff88;background:#111;}"
                + ".section{background:#1a1a2e;border-radius:12px;padding:20px;margin-bottom:20px;border:1px solid #333;}"
                + ".section h2{color:#00d4ff;margin-top:0;}"
                + "a{color:#00d4ff;text-decoration:none;}"
                + "a:hover{text-decoration:underline;}"
                + "nav{text-align:center;margin-bottom:20px;}"
                + "nav a{margin:0 15px;font-size:1.1em;}"
                + "</style></head><body>"
                + "<div class='header'><h1>AtomGuard Dashboard</h1>"
                + "<nav><a href='/'>Dashboard</a> <a href='/modules'>Moduller</a></nav></div>"
                + "<div class='grid'>"
                + "<div class='card'><h3>Online Oyuncu</h3><div class='value'>" + onlineCount + "</div></div>"
                + "<div class='card'><h3>Toplam Engelleme</h3><div class='value'>" + blockedTotal + "</div></div>"
                + "<div class='card'><h3>Aktif Modul</h3><div class='value'>" + moduleCount + "/" + totalModules + "</div></div>"
                + "<div class='card'><h3>Durum</h3><div class='value " + (isAttack ? "attack" : "normal") + "'>"
                + (isAttack ? "SALDIRI ALTINDA" : "Normal") + "</div></div>"
                + "</div>"
                + "<div class='section'><h2>Modul Istatistikleri</h2>"
                + "<table><tr><th>Modul</th><th>Durum</th><th>Engelleme</th></tr>"
                + moduleRows + "</table></div>"
                + "<div class='section'><h2>Son Olaylar</h2>"
                + "<table><tr><th>Zaman</th><th>Oyuncu</th><th>Modul</th><th>Detay</th></tr>"
                + eventRows + "</table></div>"
                + "<script>setTimeout(()=>location.reload(),10000);</script>"
                + "</body></html>";

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class ModulesPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Auth checked by ProtectedHandler

            StringBuilder moduleCards = new StringBuilder();
            for (AbstractModule module : plugin.getModuleManager().getAllModules()) {
                String statusColor = module.isEnabled() ? "#00ff88" : "#ff4444";
                String statusText = module.isEnabled() ? "Aktif" : "Kapali";
                String btnColor = module.isEnabled() ? "#ff4444" : "#00ff88";
                String btnText = module.isEnabled() ? "Kapat" : "Ac";

                moduleCards.append(String.format(
                    "<div class='mod-card'>"
                    + "<div class='mod-name'>%s</div>"
                    + "<div class='mod-status' style='color:%s'>%s</div>"
                    + "<div class='mod-blocked'>Engelleme: %d</div>"
                    + "<button onclick='toggleModule(\"%s\")' style='background:%s'>%s</button>"
                    + "</div>",
                    escapeHtml(module.getName()), statusColor, statusText,
                    module.getBlockedCount(), escapeHtml(module.getName()), btnColor, btnText));
            }

            String html = "<!DOCTYPE html><html><head><title>AtomGuard - Moduller</title>"
                + "<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:'Segoe UI',sans-serif;background:#0a0a0a;color:#eee;margin:0;padding:20px;}"
                + ".header{text-align:center;margin-bottom:30px;}"
                + "h1{color:#00ff88;margin:0;font-size:2em;}"
                + "nav{text-align:center;margin-bottom:20px;}"
                + "nav a{color:#00d4ff;margin:0 15px;font-size:1.1em;text-decoration:none;}"
                + ".mod-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:15px;}"
                + ".mod-card{background:#1a1a2e;padding:15px;border-radius:10px;border:1px solid #333;}"
                + ".mod-name{font-weight:bold;font-size:1.1em;margin-bottom:5px;}"
                + ".mod-status{font-size:0.9em;margin-bottom:5px;}"
                + ".mod-blocked{color:#888;font-size:0.85em;margin-bottom:10px;}"
                + "button{border:none;color:#fff;padding:8px 16px;border-radius:6px;cursor:pointer;font-size:0.9em;}"
                + "button:hover{opacity:0.8;}"
                + "</style></head><body>"
                + "<div class='header'><h1>Modul Yonetimi</h1>"
                + "<nav><a href='/'>Dashboard</a> <a href='/modules'>Moduller</a></nav></div>"
                + "<div class='mod-grid'>" + moduleCards + "</div>"
                + "<script>"
                + "async function toggleModule(name) {"
                + "  const res = await fetch('/api/modules/toggle', {method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'module='+encodeURIComponent(name)});"
                + "  if(res.ok) location.reload();"
                + "  else alert('Hata!');"
                + "}"
                + "</script>"
                + "</body></html>";

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Auth checked by ProtectedHandler

            long blockedTotal = plugin.getModuleManager().getTotalBlockedCount();
            int onlineCount = Bukkit.getOnlinePlayers().size();
            boolean isAttack = plugin.getAttackModeManager().isAttackMode();
            int rate = plugin.getAttackModeManager().getCurrentRate();

            long allTimeBlocked = 0;
            if (plugin.getStatisticsManager() != null) {
                allTimeBlocked = plugin.getStatisticsManager().getTotalBlockedAllTime();
            }

            String json = String.format(
                "{\"status\":\"online\",\"blocked_total\":%d,\"blocked_all_time\":%d,\"online_players\":%d,"
                + "\"attack_mode\":%b,\"connection_rate\":%d,\"modules_active\":%d,\"modules_total\":%d}",
                blockedTotal, allTimeBlocked, onlineCount, isAttack, rate,
                plugin.getModuleManager().getEnabledModuleCount(),
                plugin.getModuleManager().getTotalModuleCount());

            sendResponse(exchange, 200, "application/json", json);
        }
    }

    private class ModulesApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Auth checked by ProtectedHandler

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (AbstractModule module : plugin.getModuleManager().getAllModules()) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format(
                    "{\"name\":\"%s\",\"enabled\":%b,\"blocked\":%d,\"description\":\"%s\"}",
                    escapeJson(module.getName()), module.isEnabled(),
                    module.getBlockedCount(), escapeJson(module.getDescription())));
            }
            json.append("]");

            sendResponse(exchange, 200, "application/json", json.toString());
        }
    }

    private class ModuleToggleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Auth checked by ProtectedHandler

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body;
            try {
                body = readBodyWithLimit(exchange, 1024); // 1KB limit
            } catch (IOException e) {
                sendResponse(exchange, 413, "application/json", "{\"error\":\"Body too large\"}");
                return;
            }
            
            String moduleName = null;

            // Parse form data: module=xxx
            for (String param : body.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "module".equals(kv[0])) {
                    moduleName = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }

            if (moduleName == null || !plugin.getModuleManager().hasModule(moduleName)) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Module not found\"}");
                return;
            }

            boolean newState = plugin.getModuleManager().toggleModule(moduleName);
            sendResponse(exchange, 200, "application/json",
                String.format("{\"module\":\"%s\",\"enabled\":%b}", escapeJson(moduleName), newState));
        }
    }

    private class EventsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Auth checked by ProtectedHandler

            List<EventRecord> events = getRecentEvents();
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (EventRecord ev : events) {
                if (!first) json.append(",");
                first = false;
                json.append(String.format(
                    "{\"timestamp\":%d,\"time\":\"%s\",\"player\":\"%s\",\"module\":\"%s\",\"details\":\"%s\"}",
                    ev.timestamp, formatTimestamp(ev.timestamp),
                    escapeJson(ev.playerName), escapeJson(ev.moduleName), escapeJson(ev.details)));
            }
            json.append("]");

            sendResponse(exchange, 200, "application/json", json.toString());
        }
    }

    private class AttacksApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Auth checked by ProtectedHandler

            StringBuilder json = new StringBuilder("[");
            if (plugin.getStatisticsManager() != null) {
                List<StatisticsManager.AttackRecord> attacks = plugin.getStatisticsManager().getAttackHistory();
                boolean first = true;
                for (StatisticsManager.AttackRecord attack : attacks) {
                    if (!first) json.append(",");
                    first = false;
                    json.append(String.format(
                        "{\"start\":%d,\"end\":%d,\"start_time\":\"%s\",\"end_time\":\"%s\","
                        + "\"peak_rate\":%d,\"blocked\":%d,\"duration_sec\":%d,\"date\":\"%s\"}",
                        attack.startTime, attack.endTime,
                        formatTimestamp(attack.startTime), formatTimestamp(attack.endTime),
                        attack.peakConnectionRate, attack.blockedCount,
                        attack.getDurationSeconds(),
                        attack.date != null ? escapeJson(attack.date) : ""));
                }
            }
            json.append("]");

            sendResponse(exchange, 200, "application/json", json.toString());
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ═══════════════════════════════════════
    // Data class
    // ═══════════════════════════════════════

    public static class EventRecord {
        public long timestamp;
        public String moduleName;
        public String playerName;
        public String details;
    }
}
