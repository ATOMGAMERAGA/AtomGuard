package com.atomguard.velocity.config;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class VelocityMessageManager {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Path dataDirectory;
    private final Logger logger;
    private CommentedConfigurationNode root;

    private static final String DEFAULT_PREFIX = "<gray>[<gradient:#FF6B6B:#FF8E53>AtomGuard</gradient>]</gray> ";

    public VelocityMessageManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void load() {
        try {
            Path msgPath = dataDirectory.resolve("messages_tr.yml");
            if (!Files.exists(msgPath)) {
                try (InputStream in = getClass().getResourceAsStream("/messages_tr.yml")) {
                    if (in != null) Files.copy(in, msgPath);
                }
            }
            if (Files.exists(msgPath)) {
                root = YamlConfigurationLoader.builder().path(msgPath).build().load();
            }
        } catch (IOException e) {
            logger.warn("Mesaj dosyası yüklenemedi, varsayılanlar kullanılıyor: {}", e.getMessage());
        }
    }

    public Component getMessage(String key, TagResolver... resolvers) {
        return mm.deserialize(DEFAULT_PREFIX + getRaw(key), resolvers);
    }

    public Component buildKickMessage(String key, Map<String, String> placeholders) {
        String raw = getRaw(key);
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(e -> Placeholder.parsed(e.getKey(), e.getValue()))
                .toArray(TagResolver[]::new);
        return mm.deserialize(raw, resolvers);
    }

    public void sendMessage(CommandSource source, String key, TagResolver... resolvers) {
        source.sendMessage(getMessage(key, resolvers));
    }

    public void broadcastToAdmins(ProxyServer server, String key, TagResolver... resolvers) {
        Component msg = getMessage(key, resolvers);
        server.getAllPlayers().stream()
                .filter(p -> p.hasPermission("atomguard.admin"))
                .forEach(p -> p.sendMessage(msg));
    }

    public Component parse(String raw) {
        return mm.deserialize(raw);
    }

    public Component deserialize(String raw) {
        return mm.deserialize(raw);
    }

    public String getRaw(String key) {
        if (root == null) return "<red>[Mesaj Yok: " + key + "]</red>";
        CommentedConfigurationNode node = root.node((Object[]) key.split("\\."));
        String val = node.getString();
        return val != null ? val : "<red>[Mesaj Yok: " + key + "]</red>";
    }
}
