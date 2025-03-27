package io.specmatic.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import org.xml.sax.InputSource
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import kotlin.test.Test


class OssApplicationFunctionalTest {
    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    private val gradleProperties by lazy { projectDir.resolve("gradle.properties") }
    private val stagingRepo by lazy { projectDir.resolve("build/mvn-repo") }

    fun runner(): GradleRunner {
        return GradleRunner.create().forwardOutput().withPluginClasspath().withProjectDir(projectDir)
    }

    @Nested
    inner class ForNonMultiModuleProject {
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

            buildFile.writeText(
                """
                    plugins {
                        id("java")
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
            assertThat(result.output).contains("publishToMavenLocal")
            assertThat(result.output).contains("publishAllPublicationsToStagingRepository")
        }

        @Test
        fun `it publish single fat jar without any dependencies declared in the pom to staging repository`() {
            val result = runner().withArguments("publishAllPublicationsToStagingRepository").build()
            assertThat(result.output).contains("BUILD SUCCESSFUL")

            assertThat(getPublishedArtifacts()).isEqualTo(setOf("io.specmatic.example:example-project:1.2.3"))
            assertPublished("io.specmatic.example", "example-project", "1.2.3")

        }

        private fun getPublishedArtifacts(): Set<String> {
            val publishedPomFiles = stagingRepo.walk().filter { it.extension == "pom" }.map { it.readText() }.toList()

            val publishedArtifacts = publishedPomFiles.map { pomXml ->
                val xmlDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(InputSource(java.io.StringReader(pomXml)))
                val xPath = XPathFactory.newInstance().newXPath()
                val groupId = xPath.evaluate("/project/groupId", xmlDoc)
                val artifactId = xPath.evaluate("/project/artifactId", xmlDoc)
                val version = xPath.evaluate("/project/version", xmlDoc)
                "$groupId:$artifactId:$version"
            }

            return publishedArtifacts.toSet()
        }
    }

    private fun assertPublished(groupId: String, artifactId: String, version: String) {
        assertThat(artifactDir(groupId, artifactId, version).resolve("$artifactId-$version.jar")).exists()
        assertThat(artifactDir(groupId, artifactId, version).resolve("$artifactId-$version.pom")).exists()
    }

    private fun artifactDir(groupId: String, artifactId: String, version: String): File {
        val groupPath = groupId.replace(".", "/")
        val namePath = artifactId.replace(".", "/")
        val versionPath = version
        return stagingRepo.resolve("$groupPath/$namePath/$versionPath")
    }


}
