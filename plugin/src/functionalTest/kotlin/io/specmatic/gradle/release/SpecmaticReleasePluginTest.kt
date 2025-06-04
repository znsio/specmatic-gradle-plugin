package io.specmatic.gradle.release

import io.specmatic.gradle.AbstractFunctionalTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory

class SpecmaticReleasePluginTest : AbstractFunctionalTest() {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var remoteRepoDir: File

    override fun projectVersion(): String = "2.3.4-SNAPSHOT"

    private val logger = LoggerFactory.getLogger("test")

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
            
            specmatic {
                withOSSLibrary(rootProject) {
                }
            }
            """.trimIndent(),
        )

        writeMainClass(projectDir, "io.specmatic.example.Main")
        remoteRepoDir.execGit(logger, "init", "--bare", "--initial-branch=main")

        projectDir.execGit(logger, "add", ".")
        projectDir.execGit(logger, "commit", "-m", "Initial commit")
        projectDir.execGit(logger, "remote", "add", "origin", remoteRepoDir.absolutePath)
        projectDir.execGit(logger, "push", "-u", "origin", "main")
    }

    @Test
    fun `releaseGitPush - should create git commit before release`() {
        runWithSuccess(
            "releaseGitPush",
            "-PfunctionalTestingHack=true",
            "-Prelease.releaseVersion=7.8.1",
            "-Prelease.newVersion=7.8.5-SNAPSHOT",
        )

        val lines = projectDir.execGit(logger, "log", "-2", "--pretty=format:%s").outputUTF8().lines()
        // assert the last commit message
        assertThat(lines).containsExactly(
            "chore(release): post-release bump version 7.8.5-SNAPSHOT",
            "chore(release): pre-release bump version 7.8.1",
        )
    }
}
