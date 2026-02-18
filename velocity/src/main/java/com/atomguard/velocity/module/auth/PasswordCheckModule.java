package com.atomguard.velocity.module.auth;

import com.atomguard.velocity.AtomGuardVelocity;
import com.atomguard.velocity.module.VelocityModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;

public class PasswordCheckModule extends VelocityModule {

    private final CommonPasswordList commonList;
    private final PasswordSimilarityDetector similarityDetector;
    private final MinecraftChannelIdentifier channel;

    public PasswordCheckModule(AtomGuardVelocity plugin) {
        super(plugin, "sifre-kontrol");
        this.commonList = new CommonPasswordList(plugin);
        int maxSame = getConfigInt("benzer-sifre.ip-basina-max-ayni-sifre", 3);
        this.similarityDetector = new PasswordSimilarityDetector(maxSame);
        this.channel = MinecraftChannelIdentifier.create("atomguard", "auth");
    }

    @Override
    protected void onEnable() {
        plugin.getProxyServer().getEventManager().register(plugin, this);
        plugin.getProxyServer().getChannelRegistrar().register(channel);
        logger.info("Password Check module enabled.");
    }

    @Override
    protected void onDisable() {
        plugin.getProxyServer().getEventManager().unregisterListener(plugin, this);
        logger.info("Password Check module disabled.");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!isEnabled()) return;
        if (!event.getIdentifier().equals(channel)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subChannel = in.readUTF();

        if ("PasswordCheck".equals(subChannel)) {
            String username = in.readUTF();
            String hash = in.readUTF();
            
            Optional<Player> playerOpt = plugin.getProxyServer().getPlayer(username);
            if (playerOpt.isEmpty()) return;
            Player player = playerOpt.get();
            String ip = player.getRemoteAddress().getAddress().getHostAddress();

            checkPassword(player, ip, hash);
        }
    }

    private void checkPassword(Player player, String ip, String hash) {
        // 1. Common Password Check
        if (getConfigBoolean("ortak-sifre.aktif", true)) {
            if (commonList.isCommon(hash)) {
                String action = getConfigString("ortak-sifre.aksiyon", "uyar");
                if ("kick".equalsIgnoreCase(action)) {
                    player.disconnect(MiniMessage.miniMessage().deserialize("<red>Bu şifre çok yaygın! Lütfen değiştirin."));
                } else {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Uyarı: Kullandığınız şifre çok yaygın ve güvensiz!"));
                }
            }
        }

        // 2. Similarity Check
        if (getConfigBoolean("benzer-sifre.aktif", true)) {
            if (similarityDetector.check(ip, hash)) {
                String action = getConfigString("benzer-sifre.aksiyon", "flag");
                if ("kick".equalsIgnoreCase(action)) {
                     player.disconnect(MiniMessage.miniMessage().deserialize("<red>Güvenlik ihlali tespit edildi (Şifre benzerliği)."));
                } else if ("flag".equalsIgnoreCase(action)) {
                    logger.warn("Possible bot/alt account detected: {} from IP {} (Shared password hash)", player.getUsername(), ip);
                }
            }
        }
    }
}
