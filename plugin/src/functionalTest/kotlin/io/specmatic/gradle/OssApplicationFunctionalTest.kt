package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.impldep.org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test

class OssApplicationFunctionalTest {
    @field:TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    private val gradleProperties by lazy { projectDir.resolve("gradle.properties") }
    private val stagingRepo by lazy { projectDir.resolve("build/mvn-repo") }

    fun runner(): GradleRunner {
        return GradleRunner.create().forwardOutput().withPluginClasspath().withProjectDir(projectDir)
    }

    @BeforeEach
    fun setup() {
        gradleProperties.writeText(
            """
                version=1.2.3
                group=io.specmatic.example
            """.trimIndent()
        )

        settingsFile.writeText(
            """
                rootProject.name = "example-project"
            """.trimIndent()
        )
    }

    private fun writeMainClass(projectDir: File, mainClass: String) {
        val fileName = mainClass.replace(".", "/") + ".kt"
        val packageName = mainClass.substringBeforeLast(".")
        projectDir.resolve("src/main/kotlin/${fileName}").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package ${packageName}
                    
                    object Main {
                        @JvmStatic
                        fun main(args: Array<String>) {
                            // initialize an slf4j logger
                            // this is to ensure that obfuscation/shadowing etc work fine
                            val logger = org.slf4j.LoggerFactory.getLogger(Main::class.java)
                            logger.info("Hello, world! Version: " + VersionInfo.describe())
                            
                            // this should only print if the above works
                            println("Hello, world! Version: " + VersionInfo.describe())
                        }
                    }
                """.trimIndent()
            )
        }
    }

    @Nested
    inner class OssApplicationRootModuleOnly {
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
                        withOSSApplication(rootProject) {
                            mainClass = "io.specmatic.example.Main"
                        }
                    }
                    
                    tasks.register("runMain", JavaExec::class.java) {
                        dependsOn("publishAllPublicationsToStagingRepository")
                        classpath("build/mvn-repo/io/specmatic/example/example-project/1.2.3/example-project-1.2.3.jar")
                        mainClass = "io.specmatic.example.Main"
                    }
                """.trimIndent()

            )

            writeMainClass(projectDir, "io.specmatic.example.Main")
        }

        @Test
        fun `it should have publicationTasks`() {
            val result = runner().withArguments("tasks").build()
            assertThat(result.output).contains("publishToMavenLocal")
            assertThat(result.output).contains("publishAllPublicationsToStagingRepository")
        }

        @Test
        fun `it publish single fat jar without any dependencies declared in the pom to staging repository`() {
            val result = runner().withArguments("publishAllPublicationsToStagingRepository", "runMain").build()
            assertMainJarExecutes(result)

            assertThat(getPublishedArtifactCoordinates()).isEqualTo(setOf("io.specmatic.example:example-project:1.2.3"))
            assertPublished("io.specmatic.example", "example-project", "1.2.3")
            assertThat(getDependencies("io.specmatic.example:example-project:1.2.3")).isEmpty()

            assertThat(
                openJar("io.specmatic.example:example-project:1.2.3").stream()
                    .map { it.name })
                .contains("io/specmatic/example/VersionInfo.class")
                .contains("io/specmatic/example/version.properties")
                .contains("kotlin/Metadata.class") // kotlin is also packaged
                .contains("org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                .contains("org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                .contains("org/slf4j/Logger.class") // slf4j dependency is also packaged

            assertThat(openJar("io.specmatic.example:example-project:1.2.3").manifest.mainAttributes.getValue("Main-Class"))
                .isEqualTo("io.specmatic.example.Main")
        }
    }

    @Nested
    inner class OssApplicationMultiModuleOnly {
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
                        
                        withOSSApplication(project("executable")) {
                            mainClass = "io.specmatic.example.executable.Main"
                        }
                    }
                    
                    project(":executable") {
                        dependencies {
                          implementation(project(":core"))
                        }

                        tasks.register("runMain", JavaExec::class.java) {
                            dependsOn("publishAllPublicationsToStagingRepository")
                            classpath(rootProject.file("build/mvn-repo/io/specmatic/example/executable/1.2.3/executable-1.2.3.jar"))
                            mainClass = "io.specmatic.example.executable.Main"
                        }
                    }
                    
                """.trimIndent()
            )

            writeMainClass(projectDir.resolve("executable"), "io.specmatic.example.executable.Main")
        }

        @Test
        fun `it should have publicationTasks`() {
            val result = runner().withArguments("tasks").build()
            assertThat(result.output).contains("publishToMavenLocal")
            assertThat(result.output).contains("publishAllPublicationsToStagingRepository")
        }

        @Test
        fun `it publish single fat jar for executable with no deps, and core jar with dependencies`() {
            val result = runner().withArguments("publishAllPublicationsToStagingRepository", "runMain").build()
            assertMainJarExecutes(result)

            assertThat(getPublishedArtifactCoordinates()).isEqualTo(
                setOf(
                    "io.specmatic.example:executable:1.2.3",
                    "io.specmatic.example:core:1.2.3"
                )
            )
            assertPublished("io.specmatic.example", "executable", "1.2.3")
            assertPublished("io.specmatic.example", "core", "1.2.3")

            assertThat(getDependencies("io.specmatic.example:executable:1.2.3")).isEmpty()
            assertThat(getDependencies("io.specmatic.example:core:1.2.3")).containsExactly(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
                "org.slf4j:slf4j-api:2.0.17"
            )

            assertThat(
                openJar("io.specmatic.example:executable:1.2.3").stream()
                    .map { it.name })
                .contains("io/specmatic/example/core/VersionInfo.class") // from the core dependency
                .contains("io/specmatic/example/core/version.properties") // from the core dependency
                .contains("io/specmatic/example/executable/VersionInfo.class")
                .contains("io/specmatic/example/executable/version.properties")
                .contains("kotlin/Metadata.class") // kotlin is also packaged
                .contains("org/jetbrains/annotations/Contract.class") // kotlin is also packaged
                .contains("org/intellij/lang/annotations/Language.class") // kotlin is also packaged
                .contains("org/slf4j/Logger.class") // slf4j dependency is also packaged

            assertThat(openJar("io.specmatic.example:executable:1.2.3").manifest.mainAttributes.getValue("Main-Class"))
                .isEqualTo("io.specmatic.example.executable.Main")
        }
    }

    private fun assertMainJarExecutes(result: BuildResult) {
        assertThat(result.output).contains("No SLF4J providers were found.")
        assertThat(result.output).contains("Hello, world! Version: v1.2.3(unknown)")
        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    private fun openJar(coordinates: String): JarFile =
        JarFile(getJar(coordinates))

    private fun getJar(coordinates: String): File =
        artifactDir(coordinates).resolve("${coordinates.artifactId()}-${coordinates.version()}.jar")

    private fun getDependencies(coordinates: String): Set<String> {
        val publishedPomFiles = stagingRepo.walk().filter { it.extension == "pom" }

        val pomFile = publishedPomFiles.filter {
            val model = it.inputStream().use { MavenXpp3Reader().read(it) }
            "${model.groupId}:${model.artifactId}:${model.version}" == coordinates
        }.single()


        val model = pomFile.inputStream().use { MavenXpp3Reader().read(it) }
        return model.dependencies.map { "${it.groupId}:${it.artifactId}:${it.version}" }.toSet()
    }

    private fun getPublishedArtifactCoordinates(): Set<String> {
        val publishedPomFiles = stagingRepo.walk().filter { it.extension == "pom" }

        val publishedArtifacts = publishedPomFiles.map { pomFile ->
            val model = pomFile.inputStream().use { MavenXpp3Reader().read(it) }
            "${model.groupId}:${model.artifactId}:${model.version}"
        }

        return publishedArtifacts.toSet()
    }


    private fun assertPublished(groupId: String, artifactId: String, version: String) {
        assertThat(artifactDir(groupId, artifactId, version).resolve("$artifactId-$version.jar")).exists()
        assertThat(artifactDir(groupId, artifactId, version).resolve("$artifactId-$version.pom")).exists()
    }

    private fun artifactDir(groupId: String, artifactId: String, version: String): File {
        val groupPath = groupId.replace(".", "/")
        val namePath = artifactId.replace(".", "/")
        return stagingRepo.resolve("$groupPath/$namePath/$version")
    }

    private fun artifactDir(coordinates: String): File {
        return artifactDir(coordinates.groupId(), coordinates.artifactId(), coordinates.version())
    }

    private fun String.version() = split(":").get(2)
    private fun String.artifactId() = split(":").get(1)
    private fun String.groupId() = split(":").get(0)

}
