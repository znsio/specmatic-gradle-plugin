package io.specmatic.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.*
import com.github.jk1.license.LicenseReportPlugin
import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import net.researchgate.release.ReleasePlugin
import org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin
import org.eclipse.jgit.api.Git
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class SpecmaticGradlePluginTest {

    @Nested
    inner class LicenseReporting {
        @Test
        fun `jar, build, assemble tasks will invoke checkLicense task`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            assertThat(project.plugins.hasPlugin(LicenseReportPlugin::class.java)).isTrue()
            assertThat(project.tasks.findByName("prettyPrintLicenseCheckFailures")).isNotNull()
            assertThat(project.tasks.findByName("createAllowedLicensesFile")).isNotNull()


            assertThat(
                project.tasks.named("jar").get().finalizedBy.getDependencies(null)
            ).contains(project.tasks.named("checkLicense").get())
        }


        @Test
        fun `checkLicense task should invoke createAllowedLicensesFileTask and be finalized by prettyPrintLicenseCheckFailuresTask`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val checkLicenseTask = project.tasks.named("checkLicense").get()
            val prettyPrintLicenseCheckFailuresTask = project.tasks.named("prettyPrintLicenseCheckFailures")
            val createAllowedLicensesFileTask = project.tasks.named("createAllowedLicensesFile")

            assertThat(checkLicenseTask.taskDependencies.getDependencies(null)).contains(createAllowedLicensesFileTask.get())
            assertThat(checkLicenseTask.finalizedBy.getDependencies(null)).contains(prettyPrintLicenseCheckFailuresTask.get())
        }
    }

    @Nested
    inner class Testing {
        @Test
        fun `should add jacoco plugin if java plugin is already applied`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            assertThat(project.plugins.hasPlugin("jacoco")).isTrue()
        }

        @Test
        fun `should not add jacoco plugin if java plugin is not applied`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("io.specmatic.gradle")
            assertThat(project.plugins.hasPlugin("jacoco")).isFalse()
        }

        @Test
        fun `test tasks should be finalized by jacocoTestReport task`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val testTask = project.tasks.named("test").get()
            val jacocoTestReportTask = project.tasks.named("jacocoTestReport").get()

            assertThat(testTask.finalizedBy.getDependencies(null)).contains(jacocoTestReportTask)
        }

        @Test
        fun `configures junit platform extension`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val testTask = project.tasks.withType(org.gradle.api.tasks.testing.Test::class.java).first()
            assertThat(testTask.testFramework).isInstanceOf(JUnitPlatformTestFramework::class.java)
        }

        @Test
        fun `should configure test logger plugin`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("io.specmatic.gradle")
            assertThat(project.plugins.hasPlugin("com.adarshr.test-logger")).isTrue()
        }
    }

    @Test
    fun `adds SpecmaticGradleExtension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.specmatic.gradle")
        assertThat(project.extensions.findByType(SpecmaticGradleExtension::class.java)).isNotNull()
    }

    @Test
    fun `should configure releases plugin`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.specmatic.gradle")
        assertThat(project.plugins.hasPlugin(ReleasePlugin::class.java)).isTrue()
    }

    @Test
    fun `should configure task info plugin`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.specmatic.gradle")
        assertThat(project.plugins.hasPlugin(GradleTaskInfoPlugin::class.java)).isTrue()
    }

    @Nested
    inner class CompilerOptions {
        @Test
        fun `it should apply jvm options to java plugin if java plugin is applied`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val javaPluginExtension = project.extensions.getByType(JavaPluginExtension::class.java)
            assertThat(javaPluginExtension.toolchain.languageVersion.get().asInt()).isEqualTo(17)

            val kotlinProjectExtension = project.extensions.findByType(KotlinProjectExtension::class.java)
            assertThat(kotlinProjectExtension).isNull()
        }

        @Test
        fun `it should apply jvm options to kotlin and java if kotlin plugin is applied`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("kotlin")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val javaPluginExtension = project.extensions.getByType(JavaPluginExtension::class.java)
            assertThat(javaPluginExtension.toolchain.languageVersion.get().asInt()).isEqualTo(17)
        }
    }

    @Nested
    inner class ReproducibleArtifacts {
        @Test
        fun `should configure reproducible artifacts plugin`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val archiveTasks = project.tasks.withType(AbstractArchiveTask::class.java)
            assertThat(archiveTasks).isNotEmpty()

            archiveTasks.forEach {
                assertThat(it.isReproducibleFileOrder).isTrue()
                assertThat(it.isPreserveFileTimestamps).isFalse()
                assertThat(it.includeEmptyDirs).isFalse()
                assertThat(it.duplicatesStrategy).isEqualTo(DuplicatesStrategy.EXCLUDE)
            }
        }
    }

    @Nested
    inner class JarStamping {
        @Test
        fun `should stamp jar files with version, group, name and unknown git sha when repo is not initialized`() {
            val project = ProjectBuilder.builder().withName("test-project").build()
            project.version = "1.2.3"
            project.group = "test-group"

            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val jarTasks = project.tasks.withType(org.gradle.jvm.tasks.Jar::class.java)
            assertThat(jarTasks).isNotEmpty()

            jarTasks.forEach {
                assertThat(it.manifest.attributes["x-specmatic-version"]).isEqualTo("1.2.3")
                assertThat(it.manifest.attributes["x-specmatic-group"]).isEqualTo("test-group")
                assertThat(it.manifest.attributes["x-specmatic-name"]).isEqualTo("test-project")
                assertThat(it.manifest.attributes["x-specmatic-git-sha"]).isEqualTo("unknown - no git repo found")
            }
        }

        @Test
        fun `should stamp jar files with version, group, name and git sha with repo is initialized`(@TempDir tempDir: File) {
            val git = Git.init().setDirectory(tempDir).call()
            git.commit().setMessage("Initial commit").call()
            val headCommit = git.commit().setMessage("Second commit").call()

            val project = ProjectBuilder.builder().withProjectDir(tempDir).withName("test-project").build()
            project.version = "1.2.3"
            project.group = "test-group"

            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            val jarTasks = project.tasks.withType(org.gradle.jvm.tasks.Jar::class.java)
            assertThat(jarTasks).isNotEmpty()

            jarTasks.forEach {
                assertThat(it.manifest.attributes["x-specmatic-version"]).isEqualTo("1.2.3")
                assertThat(it.manifest.attributes["x-specmatic-group"]).isEqualTo("test-group")
                assertThat(it.manifest.attributes["x-specmatic-name"]).isEqualTo("test-project")
                assertThat(it.manifest.attributes["x-specmatic-git-sha"]).isEqualTo(headCommit.name)
            }
        }

    }

    @Nested
    inner class Sourcesets {
        @Test
        fun `should add generated resources dir to main resources if project is root project, and no subprojects exist`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            project.plugins.apply("io.specmatic.gradle")
            project.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            assertGeneratedResourcesSourceExists(project)
        }

        @Test
        fun `should add generated resources dir to main resources on java subprojects`() {
            val rootProject = ProjectBuilder.builder().withName("root").build()
            rootProject.plugins.apply("io.specmatic.gradle")

            val subProjectWithJava =
                ProjectBuilder.builder().withName("java-subproject").withParent(rootProject).build()
            subProjectWithJava.plugins.apply("java")

            val subProjectWithoutJava =
                ProjectBuilder.builder().withName("non-java-subproject").withParent(rootProject).build()

            rootProject.evaluationDependsOn(":") // force execution of `afterEvaluate` block

            assertGeneratedResourcesSourceExists(subProjectWithJava)
            assertGeneratedResourcesDoesNotExist(subProjectWithoutJava)
            assertGeneratedResourcesDoesNotExist(rootProject)
        }

        private fun assertGeneratedResourcesSourceExists(project: Project) {
            val generatedResourcesDir = project.file("src/main/resources-gen")

            val mainResources = project.the<SourceSetContainer>()["main"].resources
            assertThat(mainResources.srcDirs).contains(generatedResourcesDir)
        }

        private fun assertGeneratedResourcesDoesNotExist(project: Project) {
            assertFailure {
                project.the<SourceSetContainer>()["main"].resources
            }.hasClass(UnknownDomainObjectException::class)
        }
    }
}
