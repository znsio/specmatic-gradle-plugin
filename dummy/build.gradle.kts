// purpose of existance of this is to ensure that we always pull the latest snapshot
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    dependencies {
        classpath("io.specmatic.gradle:plugin:+") {
            isChanging = true
        }
        classpath("io.specmatic.gradle:io.specmatic.gradle.gradle.plugin:+") {
            isChanging = true
        }
    }

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}
