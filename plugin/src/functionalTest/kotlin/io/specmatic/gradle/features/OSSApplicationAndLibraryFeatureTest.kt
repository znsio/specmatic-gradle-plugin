package io.specmatic.gradle.features

import io.specmatic.gradle.AbstractFunctionalTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class OSSApplicationAndLibraryFeatureTest : AbstractFunctionalTest() {
    @Nested
    inner class RootModuleOnly {
        @BeforeEach
        fun setup() {
            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        kotlin("jvm") version "1.9.25"
                        id("io.specmatic.gradle")
                    }

                    repositories {
                        mavenCentral()
                    }
                    
                    dependencies {
                        // tiny jar, with no deps
                        implementation("org.slf4j:slf4j-api:2.0.17")
                    }
                    
                    specmatic {
                        withOSSApplicationLibrary(rootProject) {
                            mainClass = "io.specmatic.example.Main"
                        }
                    }
                    
                    tasks.register("runMain", JavaExec::class.java) {
                        dependsOn("publishAllPublicationsToStagingRepository")
                        classpath("build/mvn-repo/io/specmatic/example/example-project-all/1.2.3/example-project-all-1.2.3.jar")
                        mainClass = "io.specmatic.example.Main"
                    }
                """.trimIndent()

            )

            writeMainClass(projectDir, "io.specmatic.example.Main")
            writeLogbackXml(projectDir)
        }

        @Test
        fun `it publish single fat jar without any dependencies declared in the pom to staging repository`() {
            val result = runWithSuccess("publishAllPublicationsToStagingRepository", "runMain", "publishToMavenLocal")
            assertMainJarExecutes(result)

            assertPublishedWithJavadocAndSources(
                "io.specmatic.example:example-project:1.2.3",
                "io.specmatic.example:example-project-all:1.2.3"
            )
            assertThat(getDependencies("io.specmatic.example:example-project-all:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:example-project:1.2.3")).containsExactlyInAnyOrder(
                "org.slf4j:slf4j-api:2.0.17", "org.jetbrains.kotlin:kotlin-stdlib:1.9.25", *loggingDependencies
            )

            assertThat(
                openJar("io.specmatic.example:example-project-all:1.2.3").stream()
                    .map { it.name }).contains("io/specmatic/example/VersionInfo.class")
                .contains("io/specmatic/example/version.properties")
                .contains("kotlin/Metadata.class") // kotlin is also packaged
                .contains("org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                .contains("org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                .contains("org/slf4j/Logger.class") // slf4j dependency is also packaged

            assertThat(openJar("io.specmatic.example:example-project-all:1.2.3").manifest.mainAttributes.getValue("Main-Class")).isEqualTo(
                "io.specmatic.example.Main"
            )
        }
    }

    @Nested
    inner class RootModuleOnlyWithShadowingPrefix {
        @BeforeEach
        fun setup() {
            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        kotlin("jvm") version "1.9.25"
                        id("io.specmatic.gradle")
                    }

                    repositories {
                        mavenCentral()
                    }
                    
                    dependencies {
                        // tiny jar, with no deps
                        implementation("org.slf4j:slf4j-api:2.0.17")
                    }
                    
                    specmatic {
                        withOSSApplicationLibrary(rootProject) {
                            mainClass = "io.specmatic.example.Main"
                            shadow("example")
                        }
                    }
                    
                    tasks.register("runMain", JavaExec::class.java) {
                        dependsOn("publishAllPublicationsToStagingRepository")
                        classpath("build/mvn-repo/io/specmatic/example/example-project-all/1.2.3/example-project-all-1.2.3.jar")
                        mainClass = "io.specmatic.example.Main"
                    }
                """.trimIndent()
            )

            writeMainClass(projectDir, "io.specmatic.example.Main")
            writeLogbackXml(projectDir)
        }

        @Test
        fun `it publish single fat jar without any dependencies declared in the pom to staging repository`() {
            val result = runWithSuccess("publishAllPublicationsToStagingRepository", "runMain", "publishToMavenLocal")
            assertMainJarExecutes(result)

            assertPublishedWithJavadocAndSources(
                "io.specmatic.example:example-project:1.2.3",
                "io.specmatic.example:example-project-all:1.2.3"
            )
            assertThat(getDependencies("io.specmatic.example:example-project:1.2.3")).containsExactlyInAnyOrder(
                "org.slf4j:slf4j-api:2.0.17", "org.jetbrains.kotlin:kotlin-stdlib:1.9.25", *loggingDependencies
            )
            assertThat(getDependencies("io.specmatic.example:example-project-all:1.2.3")).isEmpty()

            assertThat(
                openJar("io.specmatic.example:example-project-all:1.2.3").stream()
                    .map { it.name }).contains("io/specmatic/example/VersionInfo.class")
                .contains("io/specmatic/example/version.properties")
                .contains("example/kotlin/Metadata.class") // kotlin is also packaged
                .contains("example/org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                .contains("example/org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                .contains("example/org/slf4j/Logger.class") // slf4j dependency is also packaged

            assertThat(openJar("io.specmatic.example:example-project-all:1.2.3").manifest.mainAttributes.getValue("Main-Class")).isEqualTo(
                "io.specmatic.example.Main"
            )
        }
    }

    @Nested
    inner class MultiModuleOnly {
        @BeforeEach
        fun setup() {
            settingsFile.appendText(
                """
                //
                include("core")
                include("executable")
                """.trimIndent()
            )

            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        kotlin("jvm") version "1.9.25"
                        id("io.specmatic.gradle")
                    }
                    
                    subprojects {
                        repositories {
                            mavenCentral()
                        }
                        
                        apply(plugin = "java")
                        apply(plugin = "org.jetbrains.kotlin.jvm")
                        
                        dependencies {
                            // tiny jar, with no deps
                            implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
                            implementation("org.slf4j:slf4j-api:2.0.17")
                        }
                    }
                    
                    specmatic {
                        withOSSLibrary(project(":core")) {
                        }
                        
                        withOSSApplicationLibrary(project("executable")) {
                            mainClass = "io.specmatic.example.executable.Main"
                        }
                    }
                    
                    project(":executable") {
                        dependencies {
                          implementation(project(":core"))
                        }

                        tasks.register("runMain", JavaExec::class.java) {
                            dependsOn("publishAllPublicationsToStagingRepository")
                            classpath(rootProject.file("build/mvn-repo/io/specmatic/example/executable-all/1.2.3/executable-all-1.2.3.jar"))
                            mainClass = "io.specmatic.example.executable.Main"
                        }
                    }
                    
                """.trimIndent()
            )

            writeMainClass(projectDir.resolve("executable"), "io.specmatic.example.executable.Main")
            writeLogbackXml(projectDir.resolve("executable"))
        }

        @Test
        fun `it publish single fat jar for executable with no deps, and core jar with dependencies`() {
            val result = runWithSuccess("publishAllPublicationsToStagingRepository", "runMain", "publishToMavenLocal")
            assertMainJarExecutes(result)

            assertPublishedWithJavadocAndSources(
                "io.specmatic.example:executable:1.2.3",
                "io.specmatic.example:executable-all:1.2.3",
                "io.specmatic.example:core:1.2.3",
            )

            assertThat(getDependencies("io.specmatic.example:executable-all:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:executable:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
                "io.specmatic.example:core:1.2.3",
                *loggingDependencies
            )
            assertThat(getDependencies("io.specmatic.example:core:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
            )

            assertThat(
                openJar("io.specmatic.example:executable-all:1.2.3").stream()
                    .map { it.name }).contains("io/specmatic/example/core/VersionInfo.class") // from the core dependency
                .contains("io/specmatic/example/core/version.properties") // from the core dependency
                .contains("io/specmatic/example/executable/VersionInfo.class")
                .contains("io/specmatic/example/executable/version.properties")
                .contains("kotlin/Metadata.class") // kotlin is also packaged
                .contains("org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                .contains("org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                .contains("org/slf4j/Logger.class") // slf4j dependency is also packaged

            assertThat(openJar("io.specmatic.example:executable-all:1.2.3").manifest.mainAttributes.getValue("Main-Class")).isEqualTo(
                "io.specmatic.example.executable.Main"
            )
        }
    }

    @Nested
    inner class MultiModuleOnlyWithShadowingPrefix {
        @BeforeEach
        fun setup() {
            settingsFile.appendText(
                """
                //
                include("core")
                include("executable")
            """.trimIndent()
            )

            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        kotlin("jvm") version "1.9.25"
                        id("io.specmatic.gradle")
                    }
                    
                    subprojects {
                        repositories {
                            mavenCentral()
                        }
                        
                        apply(plugin = "java")
                        apply(plugin = "org.jetbrains.kotlin.jvm")
                        
                        dependencies {
                            // tiny jar, with no deps
                            implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
                            implementation("org.slf4j:slf4j-api:2.0.17")
                        }
                    }
                    
                    specmatic {
                        withOSSLibrary(project(":core")) {
                        }
                        
                        withOSSApplicationLibrary(project("executable")) {
                            mainClass = "io.specmatic.example.executable.Main"
                            shadow("example")
                        }
                    }
                    
                    project(":executable") {
                        dependencies {
                          implementation(project(":core"))
                        }

                        tasks.register("runMain", JavaExec::class.java) {
                            dependsOn("publishAllPublicationsToStagingRepository")
                            classpath(rootProject.file("build/mvn-repo/io/specmatic/example/executable-all/1.2.3/executable-all-1.2.3.jar"))
                            mainClass = "io.specmatic.example.executable.Main"
                        }
                    }
                    
                """.trimIndent()
            )

            writeMainClass(projectDir.resolve("executable"), "io.specmatic.example.executable.Main")
            writeLogbackXml(projectDir.resolve("executable"))
        }

        @Test
        fun `it publish single fat jar for executable with no deps, and core jar with dependencies`() {
            val result = runWithSuccess("publishAllPublicationsToStagingRepository", "runMain", "publishToMavenLocal")
            assertMainJarExecutes(result)

            assertPublishedWithJavadocAndSources(
                "io.specmatic.example:executable:1.2.3",
                "io.specmatic.example:executable-all:1.2.3",
                "io.specmatic.example:core:1.2.3"
            )

            assertThat(getDependencies("io.specmatic.example:executable-all:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:executable:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
                "io.specmatic.example:core:1.2.3",
                *loggingDependencies
            )
            assertThat(getDependencies("io.specmatic.example:core:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25", "org.slf4j:slf4j-api:2.0.17"
            )

            assertThat(
                openJar("io.specmatic.example:executable-all:1.2.3").stream()
                    .map { it.name }).contains("example/io/specmatic/example/core/VersionInfo.class") // from the core dependency
                .contains("example/io/specmatic/example/core/version.properties") // from the core dependency
                .contains("io/specmatic/example/executable/VersionInfo.class")
                .contains("io/specmatic/example/executable/version.properties")
                .contains("example/kotlin/Metadata.class") // kotlin is also packaged
                .contains("example/org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                .contains("example/org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                .contains("example/org/slf4j/Logger.class") // slf4j dependency is also packaged

            assertThat(openJar("io.specmatic.example:executable-all:1.2.3").manifest.mainAttributes.getValue("Main-Class")).isEqualTo(
                "io.specmatic.example.executable.Main"
            )
        }
    }
}
