package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.regex.Pattern
import kotlin.test.Test

class SpecmaticGradlePluginPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }

    @Test
    fun `it should always emit exec task output`() {
        // Set up the test build
        settingsFile.writeText("")
        buildFile.writeText(
            """
            plugins {
                id("java")
                id("io.specmatic.gradle")
            }
            
            tasks.register("myExec", Exec::class.java) {
                commandLine("echo", "hello", "world")
            }
        """.trimIndent()
        )

        // Run the build
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        val result = runner.withArguments("-i", "myExec").build()

        // Verify the result
        assertThat(result.output).contains("hello world")
    }
    
    @Test
    fun `it should create version properties file`() {
        // Set up the test build
        settingsFile.writeText("rootProject.name = \"fooBar\"")
        buildFile.writeText(
            """
            plugins {
                id("java")
                id("io.specmatic.gradle")
            }
            
            version="unspecified"
            group="com.example.group"
        """.trimIndent()
        )

        // Run the build
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        val result = runner.withArguments("assemble").build()

        // Verify the result
        assertThat(result.task(":createVersionPropertiesFile")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":createVersionInfoKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val versionPropertiesFile =
            projectDir.resolve("src/main/gen-resources/com/example/group/version.properties")
        assertThat(versionPropertiesFile).exists()
        assertThat(versionPropertiesFile.readText()).contains("version=unspecified")

        val versinInfoKotlinFile = projectDir.resolve("src/main/gen-kt/com/example/group/VersionInfo.kt")
        assertThat(versinInfoKotlinFile).exists()
        assertThat(versinInfoKotlinFile.readText()).contains("val version = \"unspecified\"")
    }

    @Nested
    inner class MavenCentralPublishing {
        @Test
        fun `should not configure maven central publishing if feature is disabled`() {
            // Set up the test build
            settingsFile.writeText("")
            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        id("io.specmatic.gradle")
                    }
                    
                    specmatic {
                        withProject(rootProject) {
                            // we asked it to be published, but not specified where
                            publish() {
                                // we don't configure pom
                            }
                        }
                    }
                    version="unspecified"
                    group="com.example.group"
                """.trimIndent()
            )


            // Run the build
            val runner = GradleRunner.create()
            runner.forwardOutput()
            runner.withPluginClasspath()
            runner.withProjectDir(projectDir)
            val result = runner.withArguments("tasks", "--all").build()

            assertThat(result.output).doesNotMatch(
                Pattern.compile(
                    ".*publish.*mavencentral.*", Pattern.CASE_INSENSITIVE
                )
            )
        }

        @Test
        fun `should add maven central publishing tasks if feature is enabled`() {
            // Set up the test build
            settingsFile.writeText("")
            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        id("io.specmatic.gradle")
                    }
                    
                    specmatic {
                        publishToMavenCentral = true
                        
                        withProject(rootProject) {
                            // we asked it to be published, but also specified where
                            publish {}
                        }
                    }
                    version="unspecified"
                    group="com.example.group"
                """.trimIndent()
            )


            // Run the build
            val runner = GradleRunner.create()
            runner.forwardOutput()
            runner.withPluginClasspath()
            runner.withProjectDir(projectDir)
            val result = runner.withArguments("tasks", "--all").build()

            println(result.tasks.joinToString("\n") { it.path })
            assertThat(result.output).matches(
                Pattern.compile(
                    ".*publish\\S*MavenCentral\\S*.*", Pattern.MULTILINE or Pattern.DOTALL or Pattern.CASE_INSENSITIVE
                )
            )
        }
    }

}
