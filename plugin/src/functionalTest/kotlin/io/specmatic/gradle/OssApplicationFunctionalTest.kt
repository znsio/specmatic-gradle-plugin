package io.specmatic.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test

class OssApplicationFunctionalTest {
    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    private val gradleProperties by lazy { projectDir.resolve("gradle.properties") }

    @BeforeEach
    fun setup() {
        gradleProperties.writeText(
            """
                version=1.2.3
                group=com.example.group
            """.trimIndent()
        )

        settingsFile.writeText("")
    }

    fun runner(): GradleRunner {
        return GradleRunner.create().forwardOutput().withPluginClasspath().withProjectDir(projectDir)
    }

    @Nested
    inner class ForNonMultiModuleProject {
        @BeforeEach
        fun setup() {
            buildFile.writeText(
                """
                    plugins {
                        id("io.specmatic.gradle")
                    }
                    
                    specmatic {
                        withOSSApplication(rootProject) {
//                            githubRelease {
//                                addFile("jar", "list.jar")
//                                addFile("sourceJar", "list-src.jar")
//                                addFile("javadocJar", "list-doc.jar")
//                            }
                        }
                    }
                """.trimIndent()
            )
        }

        @Test
        fun `it should have publicationTasks`() {
            val result = runner().withArguments("tasks").build()
            assert(result.output.contains("publishToMavenLocal"))
            assert(result.output.contains("publishAllPublicationsToStagingRepository"))
        }

        @Test
        fun `it publish single jar to staging repository`() {
            val result = runner().withArguments("publishAllPublicationsToStagingRepository").build()
            assert(result.output.contains("xxx"))
        }
    }

}
