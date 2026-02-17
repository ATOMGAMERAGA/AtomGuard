package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import java.util.*;
import java.util.regex.Pattern;

public class UsernamePatternCheck extends AbstractCheck {
    
    private static final List<Pattern> BOT_PATTERNS = List.of(
        Pattern.compile("^(Bot|Player|User|Test|Hack|Attack|Spam|Storm)[-_]?\\d{1,5}$", Pattern.CASE_INSENSITIVE),
        // FP-04: Daha spesifik pattern (örn. çok uzun sayı dizisiyle bitenler)
        Pattern.compile("^[a-z]{2,3}\\d{6,10}$"),
        Pattern.compile("^\\d+[a-z]+\\d+$")
    );

    public UsernamePatternCheck(AntiBotModule module) {
        super(module, "kullanici-adi");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        String name = profile.getUsername();
        if (name == null) return 0;
        
        int score = 0;

        // 1. Bot patterns
        for (Pattern p : BOT_PATTERNS) {
            if (p.matcher(name).matches()) {
                score += 5; // 10'dan 5'e düşürüldü
                break;
            }
        }

        // 2. Length (FP-04: Kısa isim penaltisi kaldırıldı)
        // if (name.length() <= 3) score += 3;

        // 3. Entropy
        double entropyThreshold = module.getConfigDouble("kontroller.kullanici-adi.entropi-esigi", 4.0);
        if (name.length() >= 10 && calculateEntropy(name) > entropyThreshold) {
            score += 5; // 10'dan 5'e düşürüldü
        }

        // 4. Similarity
        if (module.getAttackTracker().isUnderAttack() || 
            !module.getConfigBoolean("kontroller.kullanici-adi.benzerlik-kontrolu-sadece-saldiri", true)) {
            score += checkNameSimilarity(name, module.getAttackTracker().getRecentUsernames());
        }

        module.getAttackTracker().recordUsername(name);
        return Math.min(score, 20); // 35'den 20'ye düşürüldü
    }

    private double calculateEntropy(String name) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : name.toCharArray()) {
            freq.merge(c, 1, Integer::sum);
        }
        double entropy = 0.0;
        int len = name.length();
        for (int count : freq.values()) {
            double p = (double) count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private int checkNameSimilarity(String newName, List<String> recentNames) {
        if (recentNames.size() < 5) return 0;

        int similarCount = 0;
        for (String existing : recentNames) {
            if (getLevenshteinDistance(newName, existing) <= 3) {
                similarCount++;
            }
            if (commonPrefixLength(newName, existing) >= 4 && newName.length() > 5 && existing.length() > 5) {
                similarCount++;
            }
        }

        if (similarCount > recentNames.size() * 0.3) {
            return 20;
        }
        return 0;
    }

    private int getLevenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    private int commonPrefixLength(String s1, String s2) {
        int minLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) return i;
        }
        return minLength;
    }
}
