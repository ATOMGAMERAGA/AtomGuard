package com.atomguard.notification;

import com.atomguard.AtomGuard;
import com.atomguard.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationManagerTest {
    private NotificationManager manager;
    private NotificationProvider mockProvider;

    @BeforeEach
    void setUp() {
        AtomGuard plugin = TestUtils.mockPlugin();
        manager = new NotificationManager(plugin);
        mockProvider = mock(NotificationProvider.class);
        lenient().when(mockProvider.getName()).thenReturn("test-provider");
        lenient().when(mockProvider.isEnabled()).thenReturn(true);
        lenient().when(mockProvider.sendAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void should_dispatch_to_provider_when_type_allowed() {
        manager.registerProvider(mockProvider, Set.of(NotificationType.ATTACK_MODE));
        NotificationMessage msg = NotificationMessage.of(NotificationType.ATTACK_MODE, "Test", "Desc", NotificationMessage.Severity.WARNING);
        manager.notify(msg);
        verify(mockProvider).sendAsync(msg);
    }

    @Test
    void should_not_dispatch_when_type_not_allowed() {
        manager.registerProvider(mockProvider, Set.of(NotificationType.ATTACK_MODE));
        NotificationMessage msg = NotificationMessage.of(NotificationType.BOT_KICKED, "Test", "Desc", NotificationMessage.Severity.INFO);
        manager.notify(msg);
        verify(mockProvider, never()).sendAsync(any());
    }

    @Test
    void should_not_dispatch_when_provider_disabled() {
        when(mockProvider.isEnabled()).thenReturn(false);
        manager.registerProvider(mockProvider, Set.of(NotificationType.ATTACK_MODE));
        NotificationMessage msg = NotificationMessage.of(NotificationType.ATTACK_MODE, "Test", "Desc", NotificationMessage.Severity.WARNING);
        manager.notify(msg);
        verify(mockProvider, never()).sendAsync(any());
    }

    @Test
    void should_return_provider_by_name() {
        manager.registerProvider(mockProvider, Set.of(NotificationType.ATTACK_MODE));
        assertThat(manager.getProvider("test-provider")).isPresent();
        assertThat(manager.getProvider("nonexistent")).isEmpty();
    }
}
