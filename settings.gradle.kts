pluginManagement {
    repositories {
        gradlePluginPortal()

        mavenCentral()

        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }

            content {
                includeGroup("io.specmatic.gradle")
            }
        }

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
                    settings.extra["github.actor"]?.toString()
                        ?: System.getenv("SPECMATIC_GITHUB_USER")?.toString()
                                ?: System.getenv("ORG_GRADLE_PROJECT_specmaticPrivateUsername")?.toString()
                password =
                    settings.extra["github.token"]?.toString()
                        ?: System.getenv("SPECMATIC_GITHUB_TOKEN")?.toString()
                                ?: System.getenv("ORG_GRADLE_PROJECT_specmaticPrivatePassword")?.toString()
            }
        }

    }
}

rootProject.name = "specmatic-gradle-plugin"
include("plugin", "dummy")
