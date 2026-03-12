package com.atomguard.api.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PostVerificationEventTest {

    @Test
    void should_create_event_with_correct_fields() {
        UUID uuid = UUID.randomUUID();
        PostVerificationEvent event = new PostVerificationEvent(uuid, true, "captcha");
        assertThat(event.getPlayerId()).isEqualTo(uuid);
        assertThat(event.isVerified()).isTrue();
        assertThat(event.getMethod()).isEqualTo("captcha");
    }

    @Test
    void should_be_async() {
        PostVerificationEvent event = new PostVerificationEvent(UUID.randomUUID(), true, "gravity");
        assertThat(event.isAsynchronous()).isTrue();
    }

    @Test
    void should_have_handler_list() {
        assertThat(PostVerificationEvent.getHandlerList()).isNotNull();
    }

    @Test
    void should_handle_unverified_state() {
        PostVerificationEvent event = new PostVerificationEvent(UUID.randomUUID(), false, "behavior");
        assertThat(event.isVerified()).isFalse();
    }
}
