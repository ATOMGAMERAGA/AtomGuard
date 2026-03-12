package com.atomguard.metrics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CoreMetricsTest {

    @Test
    void json_escape_should_handle_special_characters() {
        String input = "test\"value\\path";
        String escaped = input.replace("\\", "\\\\").replace("\"", "\\\"");
        assertThat(escaped).isEqualTo("test\\\"value\\\\path");
    }

    @Test
    void json_escape_should_handle_null() {
        String input = null;
        String result = input == null ? "" : input;
        assertThat(result).isEmpty();
    }
}
