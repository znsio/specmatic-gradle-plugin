pluginManagement {
    val specmaticGradlePluginVersion = settings.extra["specmaticGradlePluginVersion"] as String
    plugins {
        id("io.specmatic.gradle") version (specmaticGradlePluginVersion)
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal {
            mavenContent {
                snapshotsOnly()
            }

            content {
                includeGroup("io.specmatic.gradle")
            }
        }

        maven {
            name = "specmaticPrivate"
            url = uri("https://maven.pkg.github.com/znsio/specmatic-private-maven-repo")
            credentials {
                username =
                    settings.extra.properties["github.actor"]?.toString()
                        ?: System.getenv("SPECMATIC_GITHUB_USER")?.toString()
                                ?: System.getenv("ORG_GRADLE_PROJECT_specmaticPrivateUsername")?.toString()
                password =
                    settings.extra.properties["github.token"]?.toString()
                        ?: System.getenv("SPECMATIC_GITHUB_TOKEN")?.toString()
                                ?: System.getenv("ORG_GRADLE_PROJECT_specmaticPrivatePassword")?.toString()
            }
        }

    }
}

rootProject.name = "specmatic-gradle-plugin"
include("plugin")
