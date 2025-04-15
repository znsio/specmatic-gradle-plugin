package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CommercialApplicationLibraryFunctionalTest : AbstractFunctionalTest() {

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
                        kotlinVersion = "1.9.20"
                        withCommercialApplicationLibrary(rootProject) {
                            mainClass = "io.specmatic.example.Main"
                        }
                    }
                    
                    tasks.register("runMain", JavaExec::class.java) {
                        dependsOn("publishAllPublicationsToStagingRepository")
                        classpath(rootProject.file("build/mvn-repo/io/specmatic/example/example-project-all/1.2.3/example-project-all-1.2.3.jar"))
                        mainClass = "io.specmatic.example.Main"
                    }
                    
                    tasks.register("runMainOriginal", JavaExec::class.java) {
                        dependsOn("publishAllPublicationsToStagingRepository")
                        classpath(rootProject.file("build/mvn-repo/io/specmatic/example/example-project-all-debug/1.2.3/example-project-all-debug-1.2.3.jar"))
                        mainClass = "io.specmatic.example.Main"
                    }
                """.trimIndent()
            )

            writeRandomClasses(projectDir, "io.specmatic.example.internal.fluxcapacitor")
            writeMainClass(projectDir, "io.specmatic.example.Main", "io.specmatic.example.internal.fluxcapacitor")
        }

        @Test
        fun `it should have publicationTasks`() {
            val result = runWithSuccess("tasks")
            assertThat(result.output).contains("publishToMavenLocal")
            assertThat(result.output).contains("publishAllPublicationsToStagingRepository")
        }

        @Test
        fun `it publish single fat jar without any dependencies declared in the pom to staging repository`() {
            runWithSuccess("publishAllPublicationsToStagingRepository")

            val artifacts = arrayOf(
                "io.specmatic.example:example-project-all-debug:1.2.3",
                "io.specmatic.example:example-project-all:1.2.3",
                "io.specmatic.example:example-project:1.2.3",
            )

            assertPublished(*artifacts)

            assertThat(getDependencies("io.specmatic.example:example-project-all-debug:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:example-project-all:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:example-project:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.slf4j:slf4j-api:2.0.17",
            )

            artifacts.filter { it.contains("-all") }.forEach {
                assertThat(
                    openJar(it).stream()
                        .map { it.name })
                    .contains("io/specmatic/example/VersionInfo.class")
                    .contains("io/specmatic/example/version.properties")
                    .contains("kotlin/Metadata.class") // kotlin is also packaged
                    .contains("org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                    .contains("org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                    .contains("org/slf4j/Logger.class") // slf4j dependency is also packaged

                assertThat(openJar(it).manifest.mainAttributes.getValue("Main-Class"))
                    .isEqualTo("io.specmatic.example.Main")
            }
        }

        @Test
        fun `it should obfuscate classes`() {
            var result = runWithSuccess("runMain", "runMainOriginal")
            assertMainObfuscatedJarExecutes(result, "io.specmatic.example.internal.fluxcapacitor")
            assertMainJarExecutes(result, "io.specmatic.example.internal.fluxcapacitor")
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
                        withCommercialApplicationLibrary(rootProject) {
                            mainClass = "io.specmatic.example.Main"
                            shadow("example")
                        }
                    }
                    
                    tasks.register("runMain", JavaExec::class.java) {
                        dependsOn("publishAllPublicationsToStagingRepository")
                        classpath(rootProject.file("build/mvn-repo/io/specmatic/example/example-project-all/1.2.3/example-project-all-1.2.3.jar"))
                        mainClass = "io.specmatic.example.Main"
                    }
            
                    tasks.register("runMainOriginal", JavaExec::class.java) {
                        dependsOn("publishAllPublicationsToStagingRepository")
                        classpath(rootProject.file("build/mvn-repo/io/specmatic/example/example-project-all-debug/1.2.3/example-project-all-debug-1.2.3.jar"))
                        mainClass = "io.specmatic.example.Main"
                    }

                """.trimIndent()
            )

            writeRandomClasses(projectDir, "io.specmatic.example.internal.fluxcapacitor")
            writeMainClass(projectDir, "io.specmatic.example.Main", "io.specmatic.example.internal.fluxcapacitor")
        }

        @Test
        fun `it should have publicationTasks`() {
            val result = runWithSuccess("tasks")
            assertThat(result.output).contains("publishToMavenLocal")
            assertThat(result.output).contains("publishAllPublicationsToStagingRepository")
        }


        @Test
        fun `it publish single fat jar without any dependencies declared in the pom to staging repository`() {
            runWithSuccess("publishAllPublicationsToStagingRepository")


            val artifacts = arrayOf(
                "io.specmatic.example:example-project-all-debug:1.2.3",
                "io.specmatic.example:example-project-all:1.2.3",
                "io.specmatic.example:example-project:1.2.3"
            )

            assertPublished(*artifacts)

            assertThat(getDependencies("io.specmatic.example:example-project-all-debug:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:example-project-all:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:example-project:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
            )

            artifacts.filter { it.contains("-all") }.forEach {
                assertThat(
                    openJar(it).stream()
                        .map { it.name })
                    .contains("io/specmatic/example/VersionInfo.class")
                    .contains("io/specmatic/example/version.properties")
                    .contains("example/kotlin/Metadata.class") // kotlin is also packaged
                    .contains("example/org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                    .contains("example/org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                    .contains("example/org/slf4j/Logger.class") // slf4j dependency is also packaged

                assertThat(openJar("io.specmatic.example:example-project:1.2.3").manifest.mainAttributes.getValue("Main-Class"))
                    .isEqualTo("io.specmatic.example.Main")
            }

            @Test
            fun `it should obfuscate classes`() {
                var result = runWithSuccess("runMain", "runMainOriginal")
                assertMainObfuscatedJarExecutes(result, "io.specmatic.example.internal.fluxcapacitor")
                assertMainJarExecutes(result, "io.specmatic.example.internal.fluxcapacitor")
            }
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
                        withCommercialLibrary(project(":core")) {
                        }
                        
                        withCommercialApplicationLibrary(project(":executable")) {
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
                        
                        tasks.register("runMainOriginal", JavaExec::class.java) {
                            dependsOn("publishAllPublicationsToStagingRepository")
                            classpath(rootProject.file("build/mvn-repo/io/specmatic/example/executable-all-debug/1.2.3/executable-all-debug-1.2.3.jar"))
                            mainClass = "io.specmatic.example.executable.Main"
                        }
     
                    }
                    
                """.trimIndent()
            )

            writeRandomClasses(
                projectDir.resolve("executable"),
                "io.specmatic.example.executable.internal.fluxcapacitor"
            )
            writeMainClass(
                projectDir.resolve("executable"),
                "io.specmatic.example.executable.Main",
                "io.specmatic.example.executable.internal.fluxcapacitor"
            )
            writeRandomClasses(projectDir.resolve("core"), "io.specmatic.example.core.internal.chronocore")
        }

        @Test
        fun `it should have publicationTasks`() {
            val result = runWithSuccess("tasks")
            assertThat(result.output).contains("publishToMavenLocal")
            assertThat(result.output).contains("publishAllPublicationsToStagingRepository")
        }

        @Test
        fun `it should obfuscate`() {
            val result = runWithSuccess("runMain", "runMainOriginal")
            assertMainObfuscatedJarExecutes(result, "io.specmatic.example.executable.internal.fluxcapacitor")
            assertMainJarExecutes(result, "io.specmatic.example.executable.internal.fluxcapacitor")
        }

        @Test
        fun `it publish single fat jar for executable with no deps, and core jar with dependencies`() {
            runWithSuccess("publishAllPublicationsToStagingRepository")

            val artifacts = arrayOf(
                "io.specmatic.example:executable-all:1.2.3",
                "io.specmatic.example:executable-all-debug:1.2.3",
                "io.specmatic.example:executable:1.2.3",

                "io.specmatic.example:core-dont-use-this-unless-you-know-what-you-are-doing:1.2.3",
                "io.specmatic.example:core:1.2.3",
                "io.specmatic.example:core-all-debug:1.2.3",
                "io.specmatic.example:core-min:1.2.3"
            )

            assertPublished(*artifacts)

            assertThat(getDependencies("io.specmatic.example:executable-all:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:executable-all-debug:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:executable:1.2.3")).containsExactlyInAnyOrder(
                "io.specmatic.example:core:1.2.3",
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
            )

            assertThat(getDependencies("io.specmatic.example:core:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:core-all-debug:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:core-min:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
            )
            assertThat(getDependencies("io.specmatic.example:core-dont-use-this-unless-you-know-what-you-are-doing:1.2.3")).containsExactlyInAnyOrder(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
            )

            // original jar should be larger than obfuscated jar
            assertThat(getJar("io.specmatic.example:core-dont-use-this-unless-you-know-what-you-are-doing:1.2.3").length()).isGreaterThan(
                getJar("io.specmatic.example:core-min:1.2.3").length()
            )
            assertThat(getJar("io.specmatic.example:core-all-debug:1.2.3").length()).isGreaterThan(getJar("io.specmatic.example:core-dont-use-this-unless-you-know-what-you-are-doing:1.2.3").length())
            assertThat(getJar("io.specmatic.example:core-all-debug:1.2.3").length()).isGreaterThan(getJar("io.specmatic.example:core:1.2.3").length())

            artifacts.filter { it.contains(":executable-all") }.forEach {
                assertThat(
                    openJar(it).stream()
                        .map { it.name })
                    .contains("io/specmatic/example/core/VersionInfo.class") // from the core dependency
                    .contains("io/specmatic/example/core/version.properties") // from the core dependency
                    .contains("io/specmatic/example/executable/VersionInfo.class")
                    .contains("io/specmatic/example/executable/version.properties")
                    .contains("kotlin/Metadata.class") // kotlin is also packaged
                    .contains("org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                    .contains("org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                    .contains("org/slf4j/Logger.class") // slf4j dependency is also packaged

                assertThat(openJar(it).manifest.mainAttributes.getValue("Main-Class")).isEqualTo(
                    "io.specmatic.example.executable.Main"
                )
            }
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
                        withCommercialLibrary(project(":core")) {
                        }
                        
                        withCommercialApplicationLibrary(project(":executable")) {
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
                        
                        tasks.register("runMainOriginal", JavaExec::class.java) {
                            dependsOn("publishAllPublicationsToStagingRepository")
                            classpath(rootProject.file("build/mvn-repo/io/specmatic/example/executable-all-debug/1.2.3/executable-all-debug-1.2.3.jar"))
                            mainClass = "io.specmatic.example.executable.Main"
                        }
                    }
                    
                """.trimIndent()
            )

            writeRandomClasses(
                projectDir.resolve("executable"),
                "io.specmatic.example.executable.internal.fluxcapacitor"
            )
            writeMainClass(
                projectDir.resolve("executable"),
                "io.specmatic.example.executable.Main",
                "io.specmatic.example.executable.internal.fluxcapacitor"
            )
            writeRandomClasses(projectDir.resolve("core"), "io.specmatic.example.core.internal.chronocore")
        }

        @Test
        fun `it should have publicationTasks`() {
            val result = runWithSuccess("tasks")
            assertThat(result.output).contains("publishToMavenLocal")
            assertThat(result.output).contains("publishAllPublicationsToStagingRepository")
        }

        @Test
        fun `it should obfuscate`() {
            val result = runWithSuccess("runMain", "runMainOriginal")
            assertMainObfuscatedJarExecutes(result, "io.specmatic.example.executable.internal.fluxcapacitor")
            assertMainJarExecutes(result, "io.specmatic.example.executable.internal.fluxcapacitor")
        }

        @Test
        fun `it publish single fat jar for executable with no deps, and core jar with dependencies`() {
            runWithSuccess("publishAllPublicationsToStagingRepository")

            assertPublished(
                "io.specmatic.example:executable-all:1.2.3",
                "io.specmatic.example:executable-all-debug:1.2.3",
                "io.specmatic.example:executable:1.2.3",

                "io.specmatic.example:core-dont-use-this-unless-you-know-what-you-are-doing:1.2.3",
                "io.specmatic.example:core:1.2.3",
                "io.specmatic.example:core-all-debug:1.2.3",
                "io.specmatic.example:core-min:1.2.3"
            )

            assertThat(getDependencies("io.specmatic.example:executable-all:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:executable-all-debug:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:executable:1.2.3")).containsExactlyInAnyOrder(
                "io.specmatic.example:core:1.2.3",
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17",
            )
            assertThat(getDependencies("io.specmatic.example:core:1.2.3")).isEmpty()

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
