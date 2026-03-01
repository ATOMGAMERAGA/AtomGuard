package com.atomguard.module.auth;

import com.atomguard.AtomGuard;
import com.atomguard.listener.AuthListener;
import com.atomguard.module.AbstractModule;
import com.google.common.hash.Hashing;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CorePasswordCheckModule extends AbstractModule implements Listener {

    public CorePasswordCheckModule(@NotNull AtomGuard plugin) {
        super(plugin, "sifre-kontrol", "Auth entegrasyonu ve şifre kontrolü");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "atomguard:auth");

        // Integrate with AuthMe if available
        if (plugin.getServer().getPluginManager().getPlugin("AuthMe") != null) {
            plugin.getLogger().info("AuthMe detected. Enabling direct password check integration.");
            try {
                // Register AuthListener safely
                plugin.getServer().getPluginManager().registerEvents(new AuthListener(plugin), plugin);
            } catch (NoClassDefFoundError e) {
                plugin.getLogger().warning("AuthMe detected but class not found. Skipping AuthMe listener.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isEnabled()) return;

        // Fallback for non-AuthMe servers or command-based auth
        // Only run if AuthMe is NOT present or as a backup?
        // Let's run it anyway, but maybe check if it's a login command.
        
        String msg = event.getMessage();
        String[] args = msg.split(" ");
        if (args.length < 2) return;

        String command = args[0].toLowerCase();
        if (command.equals("/login") || command.equals("/l") || command.equals("/register") || command.equals("/reg")) {
            String password = args[1];
            // Send to Velocity
            // Using SHA-256 for common password check.
            // Note: This matches CommonPasswordList.java hashing in Velocity.
            String hash = Hashing.sha256().hashString(password, StandardCharsets.UTF_8).toString();
            
            sendToVelocity(event.getPlayer(), hash);
        }
    }

    private void sendToVelocity(org.bukkit.entity.Player player, String hash) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(b)) {
            
            out.writeUTF("PasswordCheck");
            out.writeUTF(player.getName());
            out.writeUTF(hash);
            
            player.sendPluginMessage(plugin, "atomguard:auth", b.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send password check message: " + e.getMessage());
        }
    }
}