pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://znsio.github.io/specmatic-gradle-plugin")
        }
    }
}

rootProject.name = "specmatic-gradle-plugin"
include("plugin")
