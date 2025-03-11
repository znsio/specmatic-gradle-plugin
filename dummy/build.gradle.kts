// purpose of existance of this is to ensure that we always pull the latest snapshot
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }

            content {
                includeGroup("io.specmatic.gradle")
            }
        }
        mavenLocal()
    }

    dependencies {
        classpath("io.specmatic.gradle:plugin:${project.version}") {
            isChanging = true
        }

        classpath("io.specmatic.gradle:io.specmatic.gradle.gradle.plugin:${project.version}") {
            isChanging = true
        }
    }

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

plugins {
    java
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    mavenLocal()

    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent {
            snapshotsOnly()
        }

        content {
            includeGroup("io.specmatic.gradle")
        }
    }

}

dependencies {
    implementation("io.specmatic.gradle:plugin:${project.version}")
    implementation("io.specmatic.gradle:io.specmatic.gradle.gradle.plugin:${project.version}")
}
