pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/znsio/specmatic-gradle-plugin")
        }
    }
}

rootProject.name = "specmatic-gradle-plugin"
include("plugin")

