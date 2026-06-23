// Root build file — no plugins applied at root.
// Per-module config lives in plugin/build.gradle.kts and preview-renderer/build.gradle.kts.

allprojects {
    group = providers.gradleProperty("pluginGroup").get()
    version = providers.gradleProperty("pluginVersion").get()
}
