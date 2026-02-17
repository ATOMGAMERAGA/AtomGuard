package com.atomguard.module.antibot.verification;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WhitelistManager {
    private final AntiBotModule module;
    private final Set<UUID> whitelistedPlayers = ConcurrentHashMap.newKeySet();
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public WhitelistManager(AntiBotModule module) {
        this.module = module;
        this.dataFile = new File(module.getPlugin().getDataFolder(), module.getConfigString("beyaz-liste.dosya", "whitelist.json"));
        load();
    }

    public void evaluateForWhitelist(PlayerProfile profile) {
        if (profile.getUuid() == null || whitelistedPlayers.contains(profile.getUuid())) return;

        int verificationTick = module.getConfigInt("beyaz-liste.dogrulama-suresi-tick", 600);
        int maxScore = module.getConfigInt("beyaz-liste.dogrulama-max-skor", 15);

        if (profile.getTicksSinceJoin() >= verificationTick
            && profile.getMaxThreatScore() < maxScore
            && profile.hasSentClientSettings()
            && profile.hasSentPositionPacket()) {

            whitelist(profile.getUuid());
        }
    }

    public void whitelist(UUID uuid) {
        if (whitelistedPlayers.add(uuid)) {
            saveAsync();
            module.getPlugin().getLogManager().info("Player whitelisted: " + uuid);
        }
    }

    public boolean isWhitelisted(UUID uuid) {
        if (uuid == null) return false;
        return whitelistedPlayers.contains(uuid);
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            Set<UUID> loaded = gson.fromJson(reader, new TypeToken<Set<UUID>>(){}.getType());
            if (loaded != null) whitelistedPlayers.addAll(loaded);
        } catch (IOException e) {
            module.getPlugin().getLogger().warning("Whitelist load error: " + e.getMessage());
        }
    }

    public void saveAsync() {
        Set<UUID> copy = ConcurrentHashMap.newKeySet();
        copy.addAll(whitelistedPlayers);
        module.getPlugin().getServer().getScheduler().runTaskAsynchronously(module.getPlugin(), () -> {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
                gson.toJson(copy, writer);
            } catch (IOException e) {
                module.getPlugin().getLogger().warning("Whitelist save error: " + e.getMessage());
            }
        });
    }
}
