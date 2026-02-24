package com.atomguard.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Migration işleminin sonucunu temsil eder.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class MigrationResult {

    private final boolean success;
    private final String fromVersion;
    private final String toVersion;
    private final List<String> addedKeys;
    private final List<String> removedKeys;
    private final List<String> renamedKeys;
    private final List<String> modifiedKeys;
    private final List<String> warnings;
    private final List<String> errors;
    private final long durationMs;

    private MigrationResult(Builder builder) {
        this.success = builder.success;
        this.fromVersion = builder.fromVersion;
        this.toVersion = builder.toVersion;
        this.addedKeys = Collections.unmodifiableList(new ArrayList<>(builder.addedKeys));
        this.removedKeys = Collections.unmodifiableList(new ArrayList<>(builder.removedKeys));
        this.renamedKeys = Collections.unmodifiableList(new ArrayList<>(builder.renamedKeys));
        this.modifiedKeys = Collections.unmodifiableList(new ArrayList<>(builder.modifiedKeys));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.durationMs = builder.durationMs;
    }

    public boolean isSuccess() { return success; }
    public String getFromVersion() { return fromVersion; }
    public String getToVersion() { return toVersion; }
    public List<String> getAddedKeys() { return addedKeys; }
    public List<String> getRemovedKeys() { return removedKeys; }
    public List<String> getRenamedKeys() { return renamedKeys; }
    public List<String> getModifiedKeys() { return modifiedKeys; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }
    public long getDurationMs() { return durationMs; }

    /**
     * Builder sınıfı
     */
    public static class Builder {
        private boolean success = true;
        private final String fromVersion;
        private final String toVersion;
        private final List<String> addedKeys = new ArrayList<>();
        private final List<String> removedKeys = new ArrayList<>();
        private final List<String> renamedKeys = new ArrayList<>();
        private final List<String> modifiedKeys = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private long durationMs = 0;

        public Builder(String fromVersion, String toVersion) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
        }

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder addKey(String key) { this.addedKeys.add(key); return this; }
        public Builder removeKey(String key) { this.removedKeys.add(key); return this; }
        public Builder renameKey(String desc) { this.renamedKeys.add(desc); return this; }
        public Builder modifyKey(String key) { this.modifiedKeys.add(key); return this; }
        public Builder warn(String warning) { this.warnings.add(warning); return this; }
        public Builder error(String error) { this.errors.add(error); this.success = false; return this; }
        public Builder durationMs(long ms) { this.durationMs = ms; return this; }

        public MigrationResult build() {
            return new MigrationResult(this);
        }
    }
}
