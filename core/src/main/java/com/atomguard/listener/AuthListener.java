package com.atomguard.listener;

import com.atomguard.AtomGuard;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.RegisterEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthListener implements Listener {

    private final AtomGuard plugin;

    public AuthListener(AtomGuard plugin) {
        this.plugin = plugin;
    }

    // We assume AuthMe is used. If nLogin, we would need another listener.
    // Since we can't easily detect which one and reflection is complex, we'll assume AuthMe for now as per config default.
    
    // Note: We need the RAW password to hash it ourselves to SHA-256 for Velocity to check against its list.
    // AuthMe LoginEvent does NOT provide raw password.
    // AuthMe RegisterEvent DOES provide raw password? 
    // Checking AuthMe API: RegisterEvent has getPassword(). LoginEvent does NOT.
    // So we can only check on Register.
    // For Login, we can't check unless we hook into the command pre-process?
    // /login <password>
    
    @EventHandler
    public void onRegister(RegisterEvent event) {
        sendHash(event.getPlayer(), event.getPassword());
    }
    
    // For login, we might need to listen to PlayerCommandPreprocessEvent if AuthMe doesn't expose raw password in event.
    // But that's risky and invasive.
    // Let's stick to Register check for "Common Password" detection which is most critical for new accounts.
    // For "Same Password" (Similarity), Register is also the main vector for bots creating mass accounts.
    
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
