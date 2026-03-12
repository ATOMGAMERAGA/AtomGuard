package com.atomguard.velocity.module.bedrock;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock Edition / GeyserMC / Floodgate desteği.
 *
 * <p>Bedrock oyuncularını tespit eder ve diğer modüllere {@code isBedrockPlayer} flag'i sağlar.
 * Xbox Live doğrulanmış oyunculara trust bonus verir.</p>
 *
 * <p>Config anahtarı: {@code moduller.bedrock-destek.aktif}</p>
 */
public class BedrockSupportModule extends VelocityModule {

    private final FloodgateIntegration floodgate;
    private final BedrockHandshakeValidator handshakeValidator;
    private final BedrockBotDetector botDetector;

    private final Set<String> bedrockIps = ConcurrentHashMap.newKeySet();

    private String bedrockPrefix;
    private int xboxLiveBonus;
    private double rateLimitMultiplier;

    public BedrockSupportModule(AtomGuardVelocity plugin) {
        super(plugin, "bedrock-destek");
        this.floodgate = new FloodgateIntegration(plugin);
        this.handshakeValidator = new BedrockHandshakeValidator(plugin);
        this.botDetector = new BedrockBotDetector(plugin);
    }

    @Override
    protected void onEnable() {
        this.bedrockPrefix = getConfigString("bedrock-prefix", ".");
        this.xboxLiveBonus = getConfigInt("xbox-live-bonus", 10);
        this.rateLimitMultiplier = getConfigDouble("bedrock-rate-limit-carpani", 1.5);

        if (getConfigBoolean("floodgate-entegrasyon", true)) {
            floodgate.initialize();
        }

        plugin.getProxyServer().getEventManager().register(plugin, this);
        logger.info("Bedrock Support module enabled (prefix: {})", bedrockPrefix);
    }

    @Override
    protected void onDisable() {
        plugin.getProxyServer().getEventManager().unregisterListener(plugin, this);
        bedrockIps.clear();
        logger.info("Bedrock Support module disabled.");
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!isEnabled()) return;

        String username = event.getUsername();
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        boolean isBedrock = false;

        // Check Floodgate API first
        if (floodgate.isAvailable()) {
            isBedrock = floodgate.isBedrockPlayer(event.getConnection());
        }

        // Fallback: check username prefix
        if (!isBedrock && username != null && username.startsWith(bedrockPrefix)) {
            isBedrock = true;
        }

        if (isBedrock) {
            bedrockIps.add(ip);
            logger.debug("Bedrock player detected: {} ({})", username, ip);
        }
    }

    /**
     * Verilen IP'nin Bedrock oyuncu olup olmadığını kontrol eder.
     */
    public boolean isBedrockPlayer(String ip) {
        return bedrockIps.contains(ip);
    }

    /**
     * Bedrock oyuncuları için rate limit çarpanını döndürür.
     */
    public double getRateLimitMultiplier() {
        return rateLimitMultiplier;
    }

    /**
     * Xbox Live doğrulanmış oyunculara verilecek trust bonus değeri.
     */
    public int getXboxLiveBonus() {
        return xboxLiveBonus;
    }

    public FloodgateIntegration getFloodgate() {
        return floodgate;
    }

    public BedrockHandshakeValidator getHandshakeValidator() {
        return handshakeValidator;
    }

    public BedrockBotDetector getBotDetector() {
        return botDetector;
    }
}
