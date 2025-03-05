package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test

class SpecmaticGradlePluginPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    @Test
    fun `it should emit exec task output when info is enabled`() {
        // Set up the test build
        settingsFile.writeText("")
        buildFile.writeText(
            """
            plugins {
                id('java')
                id('io.specmatic.gradle')
            }
            
            tasks.register("myExec", Exec) {
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
    fun `it should not emit exec task output when info is not enabled`() {
        // Set up the test build
        settingsFile.writeText("")
        buildFile.writeText(
            """
            plugins {
                id('java')
                id('io.specmatic.gradle')
            }
            
            tasks.register("myExec", Exec) {
                commandLine("echo", "hello", "world")
            }
        """.trimIndent()
        )

        // Run the build
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        val result = runner.withArguments("myExec").build()

        // Verify the result
        assertThat(result.output).doesNotContain("hello world")
        assertThat(result.task(":myExec")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
