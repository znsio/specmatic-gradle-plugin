pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal() {
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

rootProject.name = "specmatic-gradle-plugin"
include("plugin")
