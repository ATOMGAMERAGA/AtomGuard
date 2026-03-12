package com.atomguard.test;

import com.atomguard.AtomGuard;
import com.atomguard.manager.ConfigManager;
import com.atomguard.manager.LogManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

public final class TestUtils {
    private TestUtils() {}

    public static AtomGuard mockPlugin() {
        AtomGuard plugin = mock(AtomGuard.class);
        Logger logger = Logger.getLogger("AtomGuard-Test");
        lenient().when(plugin.getLogger()).thenReturn(logger);

        ConfigManager configManager = mock(ConfigManager.class);
        lenient().when(plugin.getConfigManager()).thenReturn(configManager);

        LogManager logManager = mock(LogManager.class);
        lenient().when(plugin.getLogManager()).thenReturn(logManager);

        FileConfiguration config = mock(FileConfiguration.class);
        lenient().when(plugin.getConfig()).thenReturn(config);

        return plugin;
    }

    public static Player mockPlayer(String name, UUID uuid, String ip) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(uuid);
        InetSocketAddress address = new InetSocketAddress(ip, 25565);
        when(player.getAddress()).thenReturn(address);
        return player;
    }
}
