package io.specmatic.gradle

import io.specmatic.gradle.artifacts.EnsureJarsAreStamped
import io.specmatic.gradle.artifacts.EnsureReproducibleArtifacts
import io.specmatic.gradle.compiler.ConfigureCompilerOptions
import io.specmatic.gradle.exec.ConfigureExecTask
import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import io.specmatic.gradle.license.LicenseReportingConfiguration
import io.specmatic.gradle.obfuscate.ObfuscateConfiguration
import io.specmatic.gradle.plugin.VersionInfo
import io.specmatic.gradle.publishing.ConfigurePublications
import io.specmatic.gradle.releases.ConfigureReleases
import io.specmatic.gradle.shadow.ShadowJarConfiguration
import io.specmatic.gradle.taskinfo.ConfigureTaskInfo
import io.specmatic.gradle.tests.ConfigureTests
import io.specmatic.gradle.versioninfo.CaptureVersionInfo
import io.specmatic.gradle.versioninfo.ConfigureVersionFiles
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class SpecmaticGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val specmaticGradleExtension = project.extensions.create("specmatic", SpecmaticGradleExtension::class.java)

        pluginDebug("Specmatic Gradle Plugin ${VersionInfo.describe()}")

        project.afterEvaluate {
            // force this plugin to be applied to all projects that have been configured with the `specmatic` block
            (specmaticGradleExtension.obfuscatedProjects.keys + specmaticGradleExtension.publicationProjects.keys + specmaticGradleExtension.shadowConfigurations.keys).toSet()
                .forEach { project ->
                    project.pluginManager.apply("java")
                }
        }

        CaptureVersionInfo(project)

        // apply whatever plugins we need to apply
        LicenseReportingConfiguration(project)
        ConfigureTests(project)
        ConfigureReleases(project)
        ConfigureTaskInfo(project)
        ConfigureVersionFiles(project)

        // setup obfuscation, shadowing, publishing...
        ObfuscateConfiguration(project)
        ShadowJarConfiguration(project)
        ConfigurePublications(project)

        // after everything is configured, we can setup the tasks to apply specmatic conventions/defaults
        ConfigureCompilerOptions(project)
        EnsureReproducibleArtifacts(project)
        EnsureJarsAreStamped(project)
        ConfigureExecTask(project)
    }
}

fun findSpecmaticExtension(project: Project): SpecmaticGradleExtension? {
    var currentProject: Project? = project
    while (currentProject != null) {
        currentProject.extensions.findByType(SpecmaticGradleExtension::class.java)?.let {
            return it
        }
        currentProject = currentProject.parent
    }
    return null
}

fun pluginDebug(message: String = "") {
    println("[Specmatic Gradle Plugin]: $message")
}

