package com.atomguard.velocity.module.antibot;

import com.atomguard.velocity.AtomGuardVelocity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NicknameBlocker {

    private final AtomGuardVelocity plugin;
    private final Set<String> exactBlock = new HashSet<>();
    private final Set<String> prefixBlock = new HashSet<>();
    private final Set<String> suffixBlock = new HashSet<>();
    private final Set<Pattern> regexBlock = new HashSet<>();
    
    private int minLength;
    private int maxLength;
    private boolean blockNumbersOnly;
    private boolean blockSpecialChars;
    private int capsThreshold;

    public NicknameBlocker(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        exactBlock.clear();
        prefixBlock.clear();
        suffixBlock.clear();
        regexBlock.clear();

        exactBlock.addAll(plugin.getConfigManager().getStringList("bot-koruma.nick-engelleme.tam-eslesme"));
        prefixBlock.addAll(plugin.getConfigManager().getStringList("bot-koruma.nick-engelleme.prefix-listesi"));
        suffixBlock.addAll(plugin.getConfigManager().getStringList("bot-koruma.nick-engelleme.suffix-listesi"));
        
        List<String> regexes = plugin.getConfigManager().getStringList("bot-koruma.nick-engelleme.regex-listesi");
        for (String regex : regexes) {
            try {
                regexBlock.add(Pattern.compile(regex));
            } catch (Exception e) {
                plugin.getSlf4jLogger().warn("Invalid regex in nickname blocker: " + regex);
            }
        }

        minLength = plugin.getConfigManager().getInt("bot-koruma.nick-engelleme.minimum-uzunluk", 3);
        maxLength = plugin.getConfigManager().getInt("bot-koruma.nick-engelleme.maksimum-uzunluk", 16);
        blockNumbersOnly = plugin.getConfigManager().getBoolean("bot-koruma.nick-engelleme.sadece-sayi-engel", true);
        blockSpecialChars = plugin.getConfigManager().getBoolean("bot-koruma.nick-engelleme.ozel-karakter-engel", true);
        capsThreshold = plugin.getConfigManager().getInt("bot-koruma.nick-engelleme.buyuk-harf-esik", 10);
    }

    public NicknameCheckResult check(String username) {
        if (!plugin.getConfigManager().getBoolean("bot-koruma.nick-engelleme.aktif", true)) {
            return NicknameCheckResult.allowed();
        }

        if (username.length() < minLength) return NicknameCheckResult.blocked("Çok kısa");
        if (username.length() > maxLength) return NicknameCheckResult.blocked("Çok uzun");

        if (exactBlock.contains(username)) return NicknameCheckResult.blocked("Yasaklı isim");

        for (String prefix : prefixBlock) {
            if (username.startsWith(prefix)) return NicknameCheckResult.blocked("Yasaklı prefix: " + prefix);
        }

        for (String suffix : suffixBlock) {
            if (username.endsWith(suffix)) return NicknameCheckResult.blocked("Yasaklı suffix: " + suffix);
        }

        for (Pattern pattern : regexBlock) {
            if (pattern.matcher(username).find()) return NicknameCheckResult.blocked("Yasaklı pattern");
        }

        if (blockNumbersOnly && username.matches("\d+")) {
            return NicknameCheckResult.blocked("Sadece sayı içeremez");
        }

        if (blockSpecialChars && !username.matches("[a-zA-Z0-9_]+")) {
             return NicknameCheckResult.blocked("Özel karakter içeremez");
        }
        
        // Caps Check: If all caps and longer than threshold?
        // Prompt says "buyuk-harf-esik: 10 # Tüm büyük harf, min kaç karakter"
        // Interpretation: If username length >= 10 and ALL caps -> Block
        if (username.length() >= capsThreshold && username.equals(username.toUpperCase()) && !username.equals(username.toLowerCase())) {
             return NicknameCheckResult.blocked("Tüm harfler büyük olamaz");
        }

        return NicknameCheckResult.allowed();
    }
    
    public static class NicknameCheckResult {
        private final boolean blocked;
        private final String reason;

        private NicknameCheckResult(boolean blocked, String reason) {
            this.blocked = blocked;
            this.reason = reason;
        }

        public static NicknameCheckResult allowed() {
            return new NicknameCheckResult(false, null);
        }

        public static NicknameCheckResult blocked(String reason) {
            return new NicknameCheckResult(true, reason);
        }

        public boolean isBlocked() {
            return blocked;
        }

        public String getReason() {
            return reason;
        }
    }
}
