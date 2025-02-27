package io.specmatic.gradle

import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class SpecmaticGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("specmatic", SpecmaticGradleExtension::class.java)

        LicenseReportingConfiguration(project)
        ConfigureTests(project)
        ConfigureReleases(project)
        ConfigureTaskInfo(project)

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