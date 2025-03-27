package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.impldep.org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarFile

open class AbstractFunctionalTest {
    @field:TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var projectDir: File
    protected val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    protected val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    protected val gradleProperties by lazy { projectDir.resolve("gradle.properties") }
    protected val stagingRepo by lazy { projectDir.resolve("build/mvn-repo") }

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

    fun writeMainClass(projectDir: File, mainClass: String) {
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

    fun runner(): GradleRunner {
        return GradleRunner.create().forwardOutput().withPluginClasspath().withProjectDir(projectDir)
    }

    fun assertMainJarExecutes(result: BuildResult) {
        assertThat(result.output).contains("No SLF4J providers were found.")
        assertThat(result.output).contains("Hello, world! Version: v1.2.3(unknown)")
        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    fun openJar(coordinates: String): JarFile = JarFile(getJar(coordinates))

    fun getJar(coordinates: String): File =
        artifactDir(coordinates).resolve("${coordinates.artifactId()}-${coordinates.version()}.jar")

    fun getDependencies(coordinates: String): Set<String> {
        val publishedPomFiles = stagingRepo.walk().filter { it.extension == "pom" }

        val pomFile = publishedPomFiles.filter {
            val model = it.inputStream().use { MavenXpp3Reader().read(it) }
            "${model.groupId}:${model.artifactId}:${model.version}" == coordinates
        }.single()


        val model = pomFile.inputStream().use { MavenXpp3Reader().read(it) }
        return model.dependencies.map { "${it.groupId}:${it.artifactId}:${it.version}" }.toSet()
    }

    fun getPublishedArtifactCoordinates(): Set<String> {
        val publishedPomFiles = stagingRepo.walk().filter { it.extension == "pom" }

        val publishedArtifacts = publishedPomFiles.map { pomFile ->
            val model = pomFile.inputStream().use { MavenXpp3Reader().read(it) }
            "${model.groupId}:${model.artifactId}:${model.version}"
        }

        return publishedArtifacts.toSet()
    }

    fun assertPublished(groupId: String, artifactId: String, version: String) {
        assertThat(artifactDir(groupId, artifactId, version).resolve("$artifactId-$version.jar")).exists()
        assertThat(artifactDir(groupId, artifactId, version).resolve("$artifactId-$version.pom")).exists()
    }

    fun artifactDir(groupId: String, artifactId: String, version: String): File {
        val groupPath = groupId.replace(".", "/")
        val namePath = artifactId.replace(".", "/")
        return stagingRepo.resolve("$groupPath/$namePath/$version")
    }

    fun artifactDir(coordinates: String): File {
        return artifactDir(coordinates.groupId(), coordinates.artifactId(), coordinates.version())
    }

    fun String.version() = split(":").get(2)
    fun String.artifactId() = split(":").get(1)
    fun String.groupId() = split(":").get(0)
}