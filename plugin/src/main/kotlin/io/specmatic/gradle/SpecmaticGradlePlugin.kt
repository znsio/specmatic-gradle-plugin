package io.specmatic.gradle

import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import io.specmatic.gradle.plugin.VersionInfo
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class SpecmaticGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val specmaticGradleExtension = project.extensions.create("specmatic", SpecmaticGradleExtension::class.java)

        if (project.hasProperty("specmatic.plugin.printVersion")) {
            // append the timestamp to the version if it is available
            println("Specmatic Gradle Plugin v${VersionInfo.describe()}")
        }

        project.afterEvaluate {
            // force this plugin to be applied to all projects that have been configured with the `specmatic` block
            (specmaticGradleExtension.obfuscatedProjects.keys + specmaticGradleExtension.publicationProjects.keys + specmaticGradleExtension.shadowConfigurations.keys).toSet()
                .forEach { project ->
                    project.pluginManager.apply("java")
                }
        }

        ConfigureVersionInfo(project)

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


