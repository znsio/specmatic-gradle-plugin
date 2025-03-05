plugins {
    id("io.specmatic.gradle") version ("0.0.3")
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("io.specmatic.gradle:plugin:0.0.2-SNAPSHOT") {
            isChanging = true
        }
    }
}

specmatic {
    publication(project(":plugin")) {
        pom {
            name = "Specmatic Gradle Plugin"
            description = "For internal use by the specmatic team"
            url = "https://specmatic.io"
            licenses {
                license {
                    name = "MIT"
                    url = "https://opensource.org/license/mit"
                }
            }
            developers {
                developer {
                    id = "specmaticBuilders"
                    name = "Specmatic Builders"
                    email = "info@specmatic.io"
                }
            }
            scm {
                connection = "https://github.com/znsio/specmatic-gradle-plugin"
                url = "https://github.com/znsio/specmatic-gradle-plugin"
            }
        }
    }

    licenseData {
        name = "com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin"
        version = "8.1.1"
        projectUrl = "https://github.com/GradleUp/shadow"
        license = "Apache-2.0"
    }
    licenseData {
        name = "com.github.johnrengelman:shadow"
        version = "8.1.1"
        projectUrl = "https://github.com/GradleUp/shadow"
        license = "Apache-2.0"
    }

    licenseData {
        name = "com.adarshr.test-logger:com.adarshr.test-logger.gradle.plugin"
        version = "4.0.0"
        projectUrl = "https://github.com/radarsh/gradle-test-logger-plugin"
        license = "Apache-2.0"
    }
    licenseData {
        name = "com.adarshr:gradle-test-logger-plugin"
        version = "4.0.0"
        projectUrl = "https://github.com/radarsh/gradle-test-logger-plugin"
        license = "Apache-2.0"
    }
    licenseData {
        name = "com.github.jk1.dependency-license-report:com.github.jk1.dependency-license-report.gradle.plugin"
        version = "2.9"
        projectUrl = "https://github.com/jk1/Gradle-License-Report"
        license = "Apache-2.0"
    }
    licenseData {
        name = "com.github.jk1:gradle-license-report"
        version = "2.9"
        projectUrl = "https://github.com/jk1/Gradle-License-Report"
        license = "Apache-2.0"
    }
    licenseData {
        name = "net.researchgate:gradle-release"
        version = "3.1.0"
        projectUrl = "https://github.com/researchgate/gradle-release"
        license = "MIT"
    }
    licenseData {
        name = "org.barfuin.gradle.taskinfo:org.barfuin.gradle.taskinfo.gradle.plugin"
        version = "2.2.0"
        projectUrl = "https://gitlab.com/barfuin/gradle-taskinfo"
        license = "Apache-2.0"
    }
}

tasks.getByName("beforeReleaseBuild") {
    dependsOn("check")
}

tasks.getByName("afterReleaseBuild") {
    dependsOn("plugin:publishPlugins")
    dependsOn("plugin:publishToMavenCentral")
}

release {
    failOnSnapshotDependencies = false
}