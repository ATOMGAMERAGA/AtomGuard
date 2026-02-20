package com.atomguard.velocity.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ConnectionHistory {

    private final Map<UUID, Deque<SessionRecord>> history = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_PER_PLAYER = 10;

    public void recordLogin(UUID uuid, String ip, String username) {
        history.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>())
            .offerFirst(new SessionRecord(ip, username, System.currentTimeMillis()));
        
        Deque<SessionRecord> h = history.get(uuid);
        while (h.size() > MAX_HISTORY_PER_PLAYER) h.pollLast();
    }

    public void recordServerSwitch(UUID uuid, String serverName) {
        Deque<SessionRecord> h = history.get(uuid);
        if (h != null && !h.isEmpty()) {
            h.peekFirst().addServer(serverName);
        }
    }

    public void recordLogout(UUID uuid) {
        Deque<SessionRecord> h = history.get(uuid);
        if (h != null && !h.isEmpty()) {
            h.peekFirst().setLogoutTime(System.currentTimeMillis());
        }
    }

    public List<SessionRecord> getHistory(UUID uuid) {
        Deque<SessionRecord> h = history.get(uuid);
        return h != null ? new ArrayList<>(h) : List.of();
    }

    public static class SessionRecord {
        private final String ip;
        private final String username;
        private final long loginTime;
        private long logoutTime = -1;
        private final List<String> serverHistory = new ArrayList<>();

        public SessionRecord(String ip, String username, long loginTime) {
            this.ip = ip;
            this.username = username;
            this.loginTime = loginTime;
        }

        public void addServer(String name) {
            serverHistory.add(name + "@" + (System.currentTimeMillis() - loginTime) / 1000 + "s");
        }

        public void setLogoutTime(long logoutTime) { this.logoutTime = logoutTime; }

        public String getIp() { return ip; }
        public String getUsername() { return username; }
        public long getLoginTime() { return loginTime; }
        public long getLogoutTime() { return logoutTime; }
        public List<String> getServerHistory() { return serverHistory; }
    }
}
