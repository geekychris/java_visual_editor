plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21.0.5"
    modules = listOf(
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.swing",
        "javafx.web",
        "javafx.media",
    )
}

application {
    mainClass.set("com.visualjava.preview.PreviewRenderer")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

tasks.shadowJar {
    archiveBaseName.set("preview-renderer")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

// Expose the shadow jar to the :plugin module via a dedicated outgoing configuration.
val rendererArtifact: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(rendererArtifact.name, tasks.shadowJar)
}
