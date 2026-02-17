package com.atomguard;

public final class BuildInfo {

    public static final String NAME = "Atom Guard";
    public static final String VERSION = "${project.version}";
    public static final String BUILD_DATE = "${build.timestamp}";
    public static final String MINECRAFT_VERSION = "1.21.4";
    public static final String API_VERSION = "1.21";
    public static final String JAVA_VERSION = "21";

    public static final int VERSION_MAJOR = 1;
    public static final int VERSION_MINOR = 0;
    public static final int VERSION_PATCH = 0;
    public static final String VERSION_TAG = "";

    public static String getFullVersion() {
        if (VERSION_TAG.isEmpty()) {
            return VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_PATCH;
        }
        return VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_PATCH + "-" + VERSION_TAG;
    }

    public static String getBanner() {
        return """

              ___  _                   ___                    _
             / _ \\| |_ ___  _ __ ___ / _ \\_   _  __ _ _ __ __| |
            / /_\\/| __/ _ \\| '_ ` _ / /_\\/ | | |/ _` | '__/ _` |
            / /_\\\\| || (_) | | | | | / /_\\\\| |_| | (_| | | | (_| |
            \\____/ \\__\\___/|_| |_| |_\\____/ \\__,_|\\__,_|_|  \\__,_|

                       v%s | Minecraft %s | Java %s
            """.formatted(getFullVersion(), MINECRAFT_VERSION, JAVA_VERSION);
    }

    private BuildInfo() {}
}
