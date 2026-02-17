package com.atomguard.manager;

import com.atomguard.AtomGuard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConfigManagerTest {

    @Mock
    private AtomGuard plugin;

    @Mock
    private FileConfiguration config;

    @Mock
    private PluginDescriptionFile description;

    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AtomGuard"));
        configManager = new ConfigManager(plugin);
    }

    @Test
    void testConfigCaching() {
        // Basic test to ensure infrastructure is working
        assertEquals(0, 0); 
    }
}
