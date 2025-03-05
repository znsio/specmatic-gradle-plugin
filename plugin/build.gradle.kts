plugins {
    `maven-publish`
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

apply(plugin = "org.jetbrains.kotlin.jvm")

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.github.jk1.dependency-license-report:com.github.jk1.dependency-license-report.gradle.plugin:2.9")
    implementation("com.adarshr.test-logger:com.adarshr.test-logger.gradle.plugin:4.0.0")
    implementation("net.researchgate:gradle-release:3.1.0")
    implementation("org.barfuin.gradle.taskinfo:org.barfuin.gradle.taskinfo.gradle.plugin:2.2.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
    implementation("com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin:8.1.1")
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.31.0-rc1")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.willowtreeapps.assertk:assertk:0.28.1")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("specmatic-gradle-plugin") {
            id = "io.specmatic.gradle"
            implementationClass = "io.specmatic.gradle.SpecmaticGradlePlugin"
            displayName = "Specmatic Gradle Plugin"
            description = buildString {
                append("This plugin is used to run Specmatic tests as part of the build process.")
                append("It ensures some standardization for build processes across specmatic repositories.")
            }
            tags = listOf("specmatic", "internal", "standardization")
        }
    }

    website = "https://specmatic.io"
    vcsUrl = "https://github.com/znsio/specmatic-gradle-plugin"
}

val functionalTestSourceSet = sourceSets.create("functionalTest")

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.named<Task>("check") {
    dependsOn(functionalTest)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = 3
}

val stagingRepo = layout.buildDirectory.dir("mvn-repo").get()

publishing {
    repositories {
        maven {
            url = stagingRepo.asFile.toURI()
        }
    }
}
