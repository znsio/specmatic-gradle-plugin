package io.specmatic.gradle

import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.jar.JarFile

@Suppress("unused")
class SpecmaticGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val specmaticGradleExtension = project.extensions.create("specmatic", SpecmaticGradleExtension::class.java)

        if (project.hasProperty("specmatic.plugin.printVersion")) {
            val jar = File(SpecmaticGradlePlugin::class.java.protectionDomain.codeSource.location.toURI().path)
            val timestamp = JarFile(jar).use { jarFile ->
                jarFile.manifest.mainAttributes.getValue("x-specmatic-compile-timestamp")
            }
            println("Specmatic Gradle Plugin loaded from $jar, timestamp: $timestamp")
        }

        project.afterEvaluate {
            // force this plugin to be applied to all projects that have been configured with the `specmatic` block
            (specmaticGradleExtension.obfuscatedProjects.keys + specmaticGradleExtension.publicationProjects.keys + specmaticGradleExtension.shadowConfigurations.keys).toSet()
                .forEach { project ->
                    project.pluginManager.apply("java")
                }
        }

        // apply whatever plugins we need to apply
        LicenseReportingConfiguration(project)
        ConfigureTests(project)
        ConfigureReleases(project)
        ConfigureTaskInfo(project)

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