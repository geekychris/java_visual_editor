plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get(),
        )
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
    }
    // IntelliJ Platform bundles Jackson at runtime; we use it only at compile time.
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
            untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
        }
    }
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
    // Auto-reload tries to hot-swap the plugin when the jar changes on disk;
    // certain extension-point changes can't be hot-swapped, so the IDE
    // requests a restart and the sandbox JVM exits ("Restart not supported").
    // Disable for a stable dev loop — we restart manually after rebuilds anyway.
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Didea.auto.reload.plugins=false")
    })

    // Disable bundled plugins that interfere with our editor in the sandbox.
    // The bundled JavaFX (Scene Builder) plugin runs slow ops on EDT in
    // response to FXML undo/edit events; turning it off makes the sandbox
    // usable. In real IntelliJ installs, users will get a one-time prompt
    // suggesting they disable it (TODO M10).
    doFirst {
        val sandboxConfig = rootDir.resolve(".intellijPlatform/sandbox/plugin/IC-2025.1/config")
        sandboxConfig.mkdirs()
        sandboxConfig.resolve("disabled_plugins.txt")
            .writeText("org.jetbrains.plugins.javaFX\n")
    }
}

// Bundle the preview-renderer fat jar into the plugin's distribution so it
// can be extracted and launched at runtime.
val rendererJar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    rendererJar(project(mapOf("path" to ":preview-renderer", "configuration" to "rendererArtifact")))
}

tasks.named<ProcessResources>("processResources") {
    from(rendererJar) {
        into("preview-renderer")
        rename { "preview-renderer.jar" }
    }
}
