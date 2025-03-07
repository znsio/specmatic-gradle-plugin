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

    }
}

rootProject.name = "specmatic-gradle-plugin"
include("plugin", "dummy")
