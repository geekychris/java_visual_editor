package com.example;

/**
 * Non-{@code Application} entrypoint.
 *
 * Java's launcher refuses to start a class that extends {@code javafx.application.Application}
 * unless the JavaFX modules are on the {@code --module-path}. The openjfx Gradle
 * plugin only sets those JVM args on the {@code :run} task — not on a default
 * IntelliJ Java Application run config — so debugging {@code HelloApp.main()}
 * directly fails with "JavaFX runtime components are missing".
 *
 * Routing through this class avoids the check; JavaFX is then found on the
 * regular classpath, which is how Gradle dependencies land.
 */
public final class Launcher {
    public static void main(String[] args) {
        HelloApp.main(args);
    }
}
