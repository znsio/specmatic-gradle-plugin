package io.specmatic.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME
import io.specmatic.gradle.artifacts.EnsureJarsAreStamped
import io.specmatic.gradle.artifacts.EnsureReproducibleArtifacts
import io.specmatic.gradle.compiler.ConfigureCompilerOptions
import io.specmatic.gradle.dock.ConfigureDockerTasks
import io.specmatic.gradle.exec.ConfigureExecTask
import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import io.specmatic.gradle.jar.massage.ObfuscateShadowAndPublish
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.massage.shadow
import io.specmatic.gradle.license.LicenseReportingConfiguration
import io.specmatic.gradle.plugin.VersionInfo
import io.specmatic.gradle.releases.ConfigureReleases
import io.specmatic.gradle.taskinfo.ConfigureTaskInfo
import io.specmatic.gradle.tests.ConfigureTests
import io.specmatic.gradle.versioninfo.CaptureVersionInfo
import io.specmatic.gradle.versioninfo.ConfigureVersionFiles
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin

@Suppress("unused")
class SpecmaticGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val specmaticGradleExtension = project.extensions.create("specmatic", SpecmaticGradleExtension::class.java)

        pluginDebug("Specmatic Gradle Plugin ${VersionInfo.describe()}")

        if (project.subprojects.isEmpty()) {
            project.applyShadowConfigs()
        } else {
            project.subprojects {
                applyShadowConfigs()
            }
        }


        project.afterEvaluate {

            // force this plugin to be applied to all projects that have been configured with the `specmatic` block
            specmaticGradleExtension.projectConfigurations.keys.forEach { project ->
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
        ObfuscateShadowAndPublish(project)

        // after everything is configured, we can setup the tasks to apply specmatic conventions/defaults
        ConfigureCompilerOptions(project)
        EnsureReproducibleArtifacts(project)
        EnsureJarsAreStamped(project)
        ConfigureExecTask(project)
        ConfigureDockerTasks(project)
    }

}

private fun Project.applyShadowConfigs() {
    plugins.withType(JavaPlugin::class.java) {
        val shadowConfiguration = configurations.create("shadow")

        val shadowRuntimeElements = configurations.create(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
            extendsFrom(shadowConfiguration)
            isCanBeConsumed = true
            isCanBeResolved = false
        }

        configurations.named("compileClasspath") {
            extendsFrom(shadowConfiguration)
        }

        val softwareComponentFactory =
            (project as ProjectInternal).services.get(SoftwareComponentFactory::class.java)
        val shadowComponent = softwareComponentFactory.adhoc(ShadowBasePlugin.COMPONENT_NAME)
        project.components.add(shadowComponent)
        shadowComponent.addVariantsFromConfiguration(shadowRuntimeElements) {
            mapToMavenScope("runtime")
        }

        tasks.jar.configure {
            from(configurations.shadow.get().files.map { zipTree(it) })
        }
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
