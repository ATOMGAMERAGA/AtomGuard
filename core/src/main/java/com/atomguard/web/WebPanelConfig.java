package com.atomguard.web;

import com.atomguard.AtomGuard;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Web panel yapilandirma sinifi.
 * Config okumalarini WebPanel constructor'indan ayirir.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class WebPanelConfig {

    private final int port;
    private final boolean enabled;
    private final boolean authEnabled;
    private final String authUser;
    private final String authPass;
    private final int maxEvents;
    private final String corsOrigin;
    private final int rateLimit;

    // JWT config
    private final String jwtSecret;
    private final int tokenExpiryMinutes;

    public WebPanelConfig(AtomGuard plugin) {
        this.port = plugin.getConfig().getInt("web-panel.port", 8080);
        this.enabled = plugin.getConfig().getBoolean("web-panel.enabled", false);
        this.authEnabled = plugin.getConfig().getBoolean("web-panel.kimlik-dogrulama.aktif", true);
        this.authUser = plugin.getConfig().getString("web-panel.kimlik-dogrulama.kullanici-adi", "admin");

        String pass = plugin.getConfig().getString("web-panel.kimlik-dogrulama.sifre", "atomguard2024");
        if ("atomguard2024".equals(pass) && enabled) {
            pass = generateRandomPassword();
            plugin.getConfig().set("web-panel.kimlik-dogrulama.sifre", pass);
            plugin.saveConfig();
            plugin.getLogger().warning("Web Panel varsayilan sifresi degistirildi. Yeni sifre config.yml dosyasinda saklanmaktadir.");
            plugin.getLogger().warning("Web Panel sifresi: " + pass.substring(0, 3) + "***" + " (config.yml: web-panel.kimlik-dogrulama.sifre)");
        }
        this.authPass = pass;

        this.maxEvents = plugin.getConfig().getInt("web-panel.max-olay-sayisi", 100);
        this.corsOrigin = plugin.getConfig().getString("web-panel.cors-origin", "");
        this.rateLimit = plugin.getConfig().getInt("web-panel.api.rate-limit", 60);

        // JWT
        String secret = plugin.getConfig().getString("web-panel.jwt.secret", "");
        if (secret == null || secret.isBlank()) {
            secret = generateJwtSecret();
            plugin.getConfig().set("web-panel.jwt.secret", secret);
            plugin.saveConfig();
            plugin.getLogger().info("[WebPanel] JWT secret otomatik olusturuldu.");
        }
        this.jwtSecret = secret;
        this.tokenExpiryMinutes = plugin.getConfig().getInt("web-panel.jwt.token-suresi-dakika", 120);
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

    private String generateJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Getters
    public int getPort() { return port; }
    public boolean isEnabled() { return enabled; }
    public boolean isAuthEnabled() { return authEnabled; }
    public String getAuthUser() { return authUser; }
    public String getAuthPass() { return authPass; }
    public int getMaxEvents() { return maxEvents; }
    public String getCorsOrigin() { return corsOrigin; }
    public int getRateLimit() { return rateLimit; }
    public String getJwtSecret() { return jwtSecret; }
    public int getTokenExpiryMinutes() { return tokenExpiryMinutes; }
}
