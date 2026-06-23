plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
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
        // HTMLEditor and WebView live here:
        "javafx.web",
        // MediaView lives here:
        "javafx.media",
    )
}

application {
    mainClass.set("com.example.HelloApp")
}
