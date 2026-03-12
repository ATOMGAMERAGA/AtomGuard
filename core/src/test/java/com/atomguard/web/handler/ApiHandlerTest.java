package com.atomguard.web.handler;

import com.atomguard.AtomGuard;
import com.atomguard.manager.AttackModeManager;
import com.atomguard.manager.ModuleManager;
import com.atomguard.manager.StatisticsManager;
import com.atomguard.web.WebPanel;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiHandlerTest {

    @Mock private AtomGuard plugin;
    @Mock private WebPanel webPanel;
    @Mock private HttpExchange exchange;
    @Mock private ModuleManager moduleManager;
    @Mock private AttackModeManager attackModeManager;
    @Mock private StatisticsManager statisticsManager;

    private ApiHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiHandler(plugin, webPanel);
    }

    @Test
    void methodNotAllowedForPost() throws IOException {
        when(exchange.getRequestMethod()).thenReturn("POST");

        Headers headers = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(headers);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(405), anyLong());
        String response = baos.toString();
        assertThat(response).contains("Method not allowed");
    }

    @Test
    void notFoundForUnknownPath() throws IOException {
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create("/api/unknown"));

        Headers headers = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(headers);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());
        String response = baos.toString();
        assertThat(response).contains("Not found");
    }

    @SuppressWarnings("unchecked")
    @Test
    void healthEndpointReturnsJsonWithStatus() throws IOException {
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create("/api/health"));

        Headers headers = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(headers);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn((Collection) Collections.emptyList());
            bukkit.when(Bukkit::getMaxPlayers).thenReturn(100);
            bukkit.when(Bukkit::getTPS).thenReturn(new double[]{20.0, 20.0, 20.0});
            bukkit.when(Bukkit::getVersion).thenReturn("Paper 1.21.4");

            handler.handle(exchange);
        }

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = baos.toString();
        assertThat(response).contains("\"status\":\"online\"");
        assertThat(response).contains("\"tps\":");
        assertThat(response).contains("\"memory\":");
        assertThat(response).contains("\"uptime_seconds\":");
        assertThat(response).contains("\"online_players\":");
    }

    @SuppressWarnings("unchecked")
    @Test
    void dashboardEndpointReturnsJsonWithStats() throws IOException {
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create("/api/dashboard"));

        when(plugin.getModuleManager()).thenReturn(moduleManager);
        when(plugin.getAttackModeManager()).thenReturn(attackModeManager);
        when(plugin.getStatisticsManager()).thenReturn(statisticsManager);

        when(moduleManager.getTotalBlockedCount()).thenReturn(42L);
        when(moduleManager.getEnabledModuleCount()).thenReturn(35);
        when(moduleManager.getTotalModuleCount()).thenReturn(40);
        when(attackModeManager.isAttackMode()).thenReturn(false);
        when(attackModeManager.getCurrentRate()).thenReturn(5);
        when(statisticsManager.getTotalBlockedAllTime()).thenReturn(1000L);
        when(webPanel.getRecentEvents()).thenReturn(Collections.emptyList());

        Headers headers = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(headers);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn((Collection) Collections.emptyList());

            handler.handle(exchange);
        }

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = baos.toString();
        assertThat(response).contains("\"blocked_total\":42");
        assertThat(response).contains("\"blocked_all_time\":1000");
        assertThat(response).contains("\"attack_mode\":false");
        assertThat(response).contains("\"modules_active\":35");
        assertThat(response).contains("\"modules_total\":40");
    }

    @Test
    void healthEndpointSetsContentTypeHeader() throws IOException {
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create("/api/health"));

        Headers headers = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(headers);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn((Collection) Collections.emptyList());
            bukkit.when(Bukkit::getMaxPlayers).thenReturn(100);
            bukkit.when(Bukkit::getTPS).thenReturn(new double[]{20.0, 20.0, 20.0});
            bukkit.when(Bukkit::getVersion).thenReturn("Paper 1.21.4");

            handler.handle(exchange);
        }

        assertThat(headers.getFirst("Content-Type")).contains("application/json");
    }
}
