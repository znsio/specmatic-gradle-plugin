package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
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
    fun baseSetup() {
        Git.init().setDirectory(projectDir).call()
        projectDir.resolve(".gitignore").writeText("")
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

    fun writeRandomClasses(projectDir: File, packageName: String) {
        repeat(5) { i ->
            val className = "Class${i + 1}"
            val fileName = "$className.kt"
            val previousClassName = if (i > 0) "Class$i" else null
            projectDir.resolve("src/main/kotlin/${packageName.replace(".", "/")}/$fileName").also {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                        package $packageName
                        
                        object $className {
                            @JvmStatic
                            fun sayHello() {
                                val stackTraceElement = Thread.currentThread().stackTrace[1]
                                println("Hello from " + stackTraceElement.className + "#" + stackTraceElement.methodName)
                                ${previousClassName?.let { "$previousClassName.sayHello()" } ?: ""}
                            }

                        }
                    """.trimIndent())
            }
        }
    }

    fun writeMainClass(projectDir: File, mainClass: String, innerPackage: String? = null) {
        val fileName = mainClass.replace(".", "/") + ".kt"
        val packageName = mainClass.substringBeforeLast(".")
        projectDir.resolve("src/main/kotlin/${fileName}").also {

            val callInnerPackageCode = if (innerPackage.orEmpty().isNotEmpty())
                """
                    println("Calling inner package")
                    $innerPackage.Class5.sayHello()
                """.trimIndent()
            else
                ""
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package $packageName
                    
                    object Main {
                        @JvmStatic
                        fun main(args: Array<String>) {
                            // initialize an slf4j logger
                            // this is to ensure that obfuscation/shadowing etc work fine
                            val logger = org.slf4j.LoggerFactory.getLogger(Main::class.java)
                            logger.info("Hello, world! Version: " + VersionInfo.describe())
                            println("Logger class is" + logger.javaClass)
                            
                            // this should only print if the above works
                            println("Hello, world! Version: " + VersionInfo.describe())

                            $callInnerPackageCode
                        }
                    }
                """.trimIndent()
            )
        }
    }

    fun runWithSuccess(vararg gradleRunArgs: String): BuildResult {
        return createRunner(gradleRunArgs).build()
    }

    fun runWithFailure(vararg gradleRunArgs: String): BuildResult {
        return createRunner(gradleRunArgs).buildAndFail()
    }

    private fun createRunner(gradleRunArgs: Array<out String>): GradleRunner =
        GradleRunner.create().forwardOutput().withPluginClasspath().withProjectDir(projectDir)
            .withArguments(
                *gradleRunArgs,
                "--stacktrace",
//                "-d"
//                "-Dorg.gradle.debug=true"
            )

    fun assertMainJarExecutes(result: BuildResult, innerPackage: String? = null) {
        assertThat(result.output).contains("No SLF4J providers were found.")
        assertThat(result.output).contains("Hello, world! Version: v1.2.3(unknown)")
        if (!innerPackage.isNullOrBlank()) {
            val expectedLines = result.output.lines()
                .filter { it.startsWith("Hello from $innerPackage") && it.contains(".internal.") }
            assertThat(expectedLines).containsExactly(
                "Hello from $innerPackage.Class5#sayHello",
                "Hello from $innerPackage.Class4#sayHello",
                "Hello from $innerPackage.Class3#sayHello",
                "Hello from $innerPackage.Class2#sayHello",
                "Hello from $innerPackage.Class1#sayHello",
            )
        }
        assertThat(result.output).contains("BUILD SUCCESSFUL")
    }

    fun assertMainObfuscatedJarExecutes(result: BuildResult, innerPackage: String? = null) {
        assertThat(result.output).contains("No SLF4J providers were found.")
        assertThat(result.output).contains("Hello, world! Version: v1.2.3(unknown)")

        if (!innerPackage.isNullOrBlank()) {
            val packageComponents = innerPackage.split(".internal.")
            val expectedLines = result.output.lines()
                .filter { it.startsWith("Hello from ${packageComponents[0]}") && !it.contains(".internal.") }
            assertThat(expectedLines)
                .containsExactlyInAnyOrder(
                    "Hello from ${packageComponents[0]}.a.a.e#a",
                    "Hello from ${packageComponents[0]}.a.a.d#a",
                    "Hello from ${packageComponents[0]}.a.a.c#a",
                    "Hello from ${packageComponents[0]}.a.a.b#a",
                    "Hello from ${packageComponents[0]}.a.a.a#a"
                )

        }
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

    private fun getPublishedArtifactCoordinates(): Set<String> {
        val publishedPomFiles = stagingRepo.walk().filter { it.extension == "pom" }

        val publishedArtifacts = publishedPomFiles.map { pomFile ->
            val model = pomFile.inputStream().use { MavenXpp3Reader().read(it) }
            "${model.groupId}:${model.artifactId}:${model.version}"
        }

        return publishedArtifacts.toSet()
    }

    fun assertPublished(vararg coordinates: String) {
        assertThat(getPublishedArtifactCoordinates()).containsExactlyInAnyOrder(*coordinates)
        coordinates.forEach {
            val groupId = it.groupId()
            val artifactId = it.artifactId()
            val version = it.version()

            val jarFiles = artifactDir(groupId, artifactId, version).listFiles { file -> file.extension == "jar" }
            assertThat(jarFiles).containsExactly(
                artifactDir(groupId, artifactId, version).resolve("${artifactId}-${version}.jar")
            )
            assertThat(artifactDir(groupId, artifactId, version).resolve("${artifactId}-${version}.pom")).exists()
        }
    }

    fun assertPublishedWithJavadocAndSources(vararg coordinates: String) {
        assertThat(getPublishedArtifactCoordinates()).containsExactlyInAnyOrder(*coordinates)
        coordinates.forEach {
            val groupId = it.groupId()
            val artifactId = it.artifactId()
            val version = it.version()

            val jarFiles = artifactDir(groupId, artifactId, version).listFiles { file -> file.extension == "jar" }

            assertThat(artifactDir(groupId, artifactId, version).resolve("${artifactId}-${version}.pom")).exists()
            assertThat(jarFiles).containsExactlyInAnyOrder(
                artifactDir(groupId, artifactId, version).resolve("${artifactId}-${version}.jar"),
                artifactDir(groupId, artifactId, version).resolve("${artifactId}-${version}-javadoc.jar"),
                artifactDir(groupId, artifactId, version).resolve("${artifactId}-${version}-sources.jar"),
            )
        }
    }

    fun assertNothingPublished() {
        assertThat(getPublishedArtifactCoordinates()).isEmpty()
    }

    private fun artifactDir(groupId: String, artifactId: String, version: String): File {
        val groupPath = groupId.replace(".", "/")
        val namePath = artifactId.replace(".", "/")
        return stagingRepo.resolve("$groupPath/$namePath/$version")
    }

    fun artifactDir(coordinates: String): File {
        return artifactDir(coordinates.groupId(), coordinates.artifactId(), coordinates.version())
    }

    private fun String.version() = split(":").get(2)
    private fun String.artifactId() = split(":").get(1)
    private fun String.groupId() = split(":").get(0)
}
