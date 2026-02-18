package com.atomguard.listener;

import com.atomguard.AtomGuard;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.events.LoginEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthListener implements Listener {

    private final AtomGuard plugin;

    public AuthListener(AtomGuard plugin) {
        this.plugin = plugin;
    }

    // AuthMe LoginEvent and RegisterEvent do NOT easily provide raw password in 5.6+
    // CorePasswordCheckModule uses PlayerCommandPreprocessEvent as a fallback.
    // This listener can be used for other AuthMe specific checks if needed.
    
    @EventHandler
    public void onLogin(LoginEvent event) {
        // Can be used for logging or other integrations
    }
    
    private void sendHash(org.bukkit.entity.Player player, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            String hexHash = bytesToHex(hash);

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PasswordCheck");
            out.writeUTF(player.getName());
            out.writeUTF(hexHash);

            player.sendPluginMessage(plugin, "atomguard:auth", out.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hash password for check: " + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}