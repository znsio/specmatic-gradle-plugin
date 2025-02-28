pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal() {
            mavenContent {
                snapshotsOnly()
            }
        }
        maven {
            url = uri("https://znsio.github.io/specmatic-gradle-plugin")
        }
    }
}

rootProject.name = "specmatic-gradle-plugin"
include("plugin")
