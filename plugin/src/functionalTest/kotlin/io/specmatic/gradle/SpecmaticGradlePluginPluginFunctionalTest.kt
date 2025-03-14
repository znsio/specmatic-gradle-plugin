package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarFile
import java.util.regex.Pattern
import kotlin.test.Test

class SpecmaticGradlePluginPluginFunctionalTest {

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
    }

    @Test
    fun `throws error when shadow prefix is not valid package name`() {
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
                    shadow("bad-package") 
                }
            }
        """.trimIndent()
        )

        // Run the build
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        assertThatCode { runner.withArguments("tasks").build() }
            .isInstanceOf(UnexpectedBuildFailure::class.java)
            .hasMessageContaining("Unexpected build execution failure")
            .hasMessageContaining("Invalid Java package name: bad-package")
    }

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

    @Nested
    inner class VersionPropertiesFile {
        @Test
        fun `it should create version properties file for non-multi-module-project`() {
            // Set up the test build
            settingsFile.writeText("rootProject.name = \"fooBar\"")
            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        id("io.specmatic.gradle")
                    }
                    
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
            assertThat(versionPropertiesFile.readText()).contains("version=1.2.3")

            val versinInfoKotlinFile = projectDir.resolve("src/main/gen-kt/com/example/group/VersionInfo.kt")
            assertThat(versinInfoKotlinFile).exists()
            assertThat(versinInfoKotlinFile.readText()).contains("val version = \"1.2.3\"")
        }

        @Test
        fun `it should create version properties file for multi-module-project`() {
            // Set up the test build
            settingsFile.writeText(
                """
                rootProject.name = "fooBar"
                include("project-a")
                include("project-b")
            """.trimIndent()
            )

            buildFile.writeText(
                """
            plugins {
                id("java")
                id("io.specmatic.gradle")
            }
                        
            project(":project-a") {
               apply(plugin = "java")
            }
            // nothing applied to project-b
            """.trimIndent()
            )
            // Run the build
            val runner = GradleRunner.create()
            runner.forwardOutput()
            runner.withPluginClasspath()
            runner.withProjectDir(projectDir)
            val result = runner.withArguments("assemble").build()

            // Verify the result
            assertThat(result.task(":project-a:createVersionPropertiesFile")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.task(":project-a:createVersionInfoKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            assertThat(projectDir.resolve("src/main/gen-resources/com/example/group/version.properties")).doesNotExist()
            assertThat(projectDir.resolve("src/main/gen-kt/com/example/group/VersionInfo.kt")).doesNotExist()

            assertThat(projectDir.resolve("project-a/src/main/gen-resources/com/example/group/project/a/version.properties")).exists()
            assertThat(projectDir.resolve("project-a/src/main/gen-kt/com/example/group/project/a/VersionInfo.kt")).exists()

            // no generated resources dir itself
            assertThat(projectDir.resolve("project-b/src/main/gen-resources")).doesNotExist()
            assertThat(projectDir.resolve("project-b/src/main/gen-kt")).doesNotExist()
        }
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
                        publishToMavenCentral()
                        
                        withProject(rootProject) {
                            // we asked it to be published, but also specified where
                            publish {}
                        }
                    }
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

    @Nested
    inner class MainClassAttribute {

        @Test
        fun `adds main class to jars if specified in project`() {
            // Set up the test build
            settingsFile.writeText("rootProject.name = \"fooBar\"")
            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        id("io.specmatic.gradle")
                    }
                    
                    specmatic {
                        publishToMavenCentral()
                        
                        withProject(rootProject) {
                            applicationMainClass = "org.example.Main"
                            
                            // we asked it to be published, but also specified where
                            publish {}
                        }
                    }
                    
                    tasks.register("customJar", Jar::class.java) {
                        archiveBaseName.set("customJar")
                        from("src/main/resources")
                    }
                    
                """.trimIndent()
            )

            val runner = GradleRunner.create()
            runner.forwardOutput()
            runner.withPluginClasspath()
            runner.withProjectDir(projectDir)
            val result = runner.withArguments("jar", "customJar", "-i").build()
            assertThat(result.task(":jar")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val jarFile = projectDir.resolve("build/libs/fooBar-1.2.3.jar")
            assertThat(jarFile).exists()

            assertThat(JarFile(jarFile).manifest.mainAttributes.getValue("Main-Class")).isEqualTo("org.example.Main")

            val customJar = projectDir.resolve("build/libs/customJar-1.2.3.jar")
            assertThat(customJar).exists()
            assertThat(JarFile(customJar).manifest.mainAttributes.getValue("Main-Class")).isEqualTo("org.example.Main")
        }

        @Test
        fun `should not add main class to jar if not specified in project`() {
            // Set up the test build
            settingsFile.writeText("rootProject.name = \"fooBar\"")
            buildFile.writeText(
                """
                    plugins {
                        id("java")
                        id("io.specmatic.gradle")
                    }
                    
                    specmatic {
                        publishToMavenCentral()
                        
                        withProject(rootProject) {
                            // we asked it to be published, but also specified where
                            publish {}
                        }
                    }
                    
                    tasks.register("customJar", Jar::class.java) {
                        archiveBaseName.set("customJar")
                        from("src/main/resources")
                    }
                    
                """.trimIndent()
            )

            val runner = GradleRunner.create()
            runner.forwardOutput()
            runner.withPluginClasspath()
            runner.withProjectDir(projectDir)
            val result = runner.withArguments("jar", "customJar").build()
            assertThat(result.task(":jar")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val jarFile = projectDir.resolve("build/libs/fooBar-1.2.3.jar")
            assertThat(jarFile).exists()

            assertThat(JarFile(jarFile).manifest.mainAttributes.containsValue("Main-Class")).isFalse()

            val customJar = projectDir.resolve("build/libs/customJar-1.2.3.jar")
            assertThat(customJar).exists()
            assertThat(JarFile(customJar).manifest.mainAttributes.containsValue("Main-Class")).isFalse()
        }
    }

}
