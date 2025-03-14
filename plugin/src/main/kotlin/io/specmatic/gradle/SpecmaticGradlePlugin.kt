package io.specmatic.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME
import io.specmatic.gradle.artifacts.EnsureJarsAreStampedPlugin
import io.specmatic.gradle.artifacts.EnsureReproducibleArtifactsPlugin
import io.specmatic.gradle.compiler.ConfigureCompilerOptionsPlugin
import io.specmatic.gradle.dock.DockerPlugin
import io.specmatic.gradle.exec.ConfigureExecTaskPlugin
import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.massage.shadow
import io.specmatic.gradle.jar.obfuscate.ObfuscateJarPlugin
import io.specmatic.gradle.jar.publishing.ConfigurePublicationsPlugin
import io.specmatic.gradle.license.SpecmaticLicenseReportingPlugin
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.plugin.VersionInfo
import io.specmatic.gradle.shadow.ShadowJarsPlugin
import io.specmatic.gradle.tests.SpecmaticTestReportingPlugin
import io.specmatic.gradle.versioninfo.VersionInfoPlugin
import io.specmatic.gradle.versioninfo.versionInfo
import net.researchgate.release.ReleasePlugin
import org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin

@Suppress("unused")
class SpecmaticGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val specmaticGradleExtension = target.extensions.create("specmatic", SpecmaticGradleExtension::class.java)

        target.pluginInfo("Specmatic Gradle Plugin ${VersionInfo.describe()}")

        target.applyToRootProjectOrSubprojects {
            applyShadowConfigs()
        }

        target.rootProject.versionInfo()

        // apply whatever plugins we need to apply
        target.plugins.apply(SpecmaticLicenseReportingPlugin::class.java)
        target.allprojects {
            plugins.apply(SpecmaticTestReportingPlugin::class.java)
        }
        target.plugins.apply(ReleasePlugin::class.java)
        target.plugins.apply(GradleTaskInfoPlugin::class.java)

        target.applyToRootProjectOrSubprojects {
            plugins.apply(VersionInfoPlugin::class.java)
        }

        target.applyToRootProjectOrSubprojects {
            // obfuscation needs to happen before shadowing!
            plugins.apply(ObfuscateJarPlugin::class.java)
            plugins.apply(ShadowJarsPlugin::class.java)
            // publishing needs to happen after shadow
            plugins.apply(ConfigurePublicationsPlugin::class.java)

            plugins.apply(ConfigureCompilerOptionsPlugin::class.java)
            plugins.apply(EnsureReproducibleArtifactsPlugin::class.java)
            plugins.apply(EnsureJarsAreStampedPlugin::class.java)
            plugins.apply(ConfigureExecTaskPlugin::class.java)
        }

        target.allprojects {
            plugins.apply(DockerPlugin::class.java)
        }
    }

    private fun Project.applyToRootProjectOrSubprojects(block: Project.() -> Unit) {
        if (subprojects.isEmpty()) {
            // apply on self
            block()
        } else {
            subprojects {
                // apply on eachSubproject
                block()
            }
        }
    }
}

private fun Project.applyShadowConfigs() {
    plugins.withType(JavaPlugin::class.java) {
        pluginInfo("Applying shadow configurations to project $name")
        val shadowConfiguration = configurations.create("shadow")

        val shadowRuntimeElements = configurations.create(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
            extendsFrom(shadowConfiguration)
            isCanBeConsumed = true
            isCanBeResolved = false
        }

        configurations.named("compileClasspath") {
            extendsFrom(shadowConfiguration)
        }

        configurations.named("testImplementation") {
            extendsFrom(shadowConfiguration)
        }

        configurations.named("testRuntimeOnly") {
            extendsFrom(shadowConfiguration)
        }

        val softwareComponentFactory = (project as ProjectInternal).services.get(SoftwareComponentFactory::class.java)
        val shadowComponent = softwareComponentFactory.adhoc(ShadowBasePlugin.COMPONENT_NAME)
        project.components.add(shadowComponent)
        shadowComponent.addVariantsFromConfiguration(shadowRuntimeElements) {
            mapToMavenScope("runtime")
        }

        tasks.jar.configure {
            from(provider { configurations.shadow.get().files.map { zipTree(it) } })
        }
    }

}

fun Project.specmaticExtension(): SpecmaticGradleExtension {
    var currentProject: Project? = this
    while (currentProject != null) {
        currentProject.extensions.findByType(SpecmaticGradleExtension::class.java)?.let {
            return it
        }
        currentProject = currentProject.parent
    }
    throw GradleException("SpecmaticGradleExtension not found in project ${this}, or any of its parents")
}

//fun pluginDebug(message: String = "") {
//    println("[Specmatic Gradle Plugin]: $message")
//}
