package com.atomguard.velocity.module.firewall;

import com.atomguard.velocity.AtomGuardVelocity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AccountBlacklist {

    private final AtomGuardVelocity plugin;
    private final Path blacklistFile;
    private final Set<String> blockedUsernames = new HashSet<>();
    private final Set<UUID> blockedUuids = new HashSet<>();
    private final Gson gson = new Gson();

    public AccountBlacklist(AtomGuardVelocity plugin) {
        this.plugin = plugin;
        this.blacklistFile = plugin.getDataDirectory().resolve("account_blacklist.json");
        load();
    }

    public void load() {
        if (!Files.exists(blacklistFile)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(blacklistFile)) {
            Type type = new TypeToken<BlacklistData>() {}.getType();
            BlacklistData data = gson.fromJson(reader, type);
            if (data != null) {
                if (data.usernames != null) blockedUsernames.addAll(data.usernames);
                if (data.uuids != null) blockedUuids.addAll(data.uuids);
            }
        } catch (IOException e) {
            plugin.getSlf4jLogger().error("Failed to load account blacklist", e);
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(blacklistFile)) {
            BlacklistData data = new BlacklistData(blockedUsernames, blockedUuids);
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getSlf4jLogger().error("Failed to save account blacklist", e);
        }
    }

    public boolean isBlacklisted(String username) {
        return blockedUsernames.contains(username.toLowerCase());
    }

    public boolean isBlacklisted(UUID uuid) {
        return blockedUuids.contains(uuid);
    }

    public void addName(String username) {
        blockedUsernames.add(username.toLowerCase());
        save();
    }

    public void addUuid(UUID uuid) {
        blockedUuids.add(uuid);
        save();
    }

    public void removeName(String username) {
        blockedUsernames.remove(username.toLowerCase());
        save();
    }

    public void removeUuid(UUID uuid) {
        blockedUuids.remove(uuid);
        save();
    }

    private static class BlacklistData {
        Set<String> usernames;
        Set<UUID> uuids;

        BlacklistData(Set<String> usernames, Set<UUID> uuids) {
            this.usernames = usernames;
            this.uuids = uuids;
        }
    }
}