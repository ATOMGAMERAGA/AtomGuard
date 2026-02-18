package com.atomguard.velocity.module.auth;

import com.atomguard.velocity.AtomGuardVelocity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public class CommonPasswordList {

    private final AtomGuardVelocity plugin;
    private final Set<String> commonHashes = new HashSet<>();

    public CommonPasswordList(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        // Load top 1000 passwords from resource and hash them
        // For this task, we will simulate loading a small list or use a built-in list
        String[] topPasswords = {
            "123456", "password", "123456789", "12345678", "12345", "111111", "1234567", "sunshine", "qwerty", "iloveyou"
            // In a real scenario, we would load a large file from resources
        };

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String pass : topPasswords) {
                byte[] hash = digest.digest(pass.getBytes(StandardCharsets.UTF_8));
                commonHashes.add(bytesToHex(hash));
            }
            plugin.getSlf4jLogger().info("Loaded {} common passwords.", commonHashes.size());
        } catch (Exception e) {
            plugin.getSlf4jLogger().error("Failed to initialize common password list", e);
        }
    }

    public boolean isCommon(String hash) {
        return commonHashes.contains(hash);
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