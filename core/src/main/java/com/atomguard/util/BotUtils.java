package com.atomguard.util;

import com.atomguard.AtomGuard;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BotUtils {

    /**
     * Calculates the Levenshtein distance between two strings.
     * This measures the minimum number of single-character edits (insertions, deletions, or substitutions)
     * required to change one word into the other.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return The distance (int)
     */
    public static int getLevenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }

    /**
     * Logs a panic ban to a specific file.
     * @param plugin The plugin instance
     * @param playerName The name of the banned player
     * @param ip The IP of the banned player
     * @param fileName The log file name
     */
    public static void logPanicBan(AtomGuard plugin, String playerName, String ip, String fileName) {
        File logFile = new File(plugin.getDataFolder(), fileName);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Panic log dosyası oluşturulamadı: " + e.getMessage());
                return;
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write(String.format("[%s] BANNED: Name=%s, IP=%s", timestamp, playerName, ip));
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().severe("Panic log dosyasına yazılamadı: " + e.getMessage());
        }
    }
}
