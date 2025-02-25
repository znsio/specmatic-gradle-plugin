package io.specmatic.gradle

import io.specmatic.gradle.extensions.SpecmaticGradleExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

class ConfigureCompilerOptions(project: Project) {
    init {
        project.allprojects.forEach {
            it.afterEvaluate(::configure)
        }
    }

    private fun configure(project: Project) {
        val specmaticGradleExtension =
            findSpecmaticExtension(project) ?: throw GradleException("SpecmaticGradleExtension not found")

        val jvmVersion = specmaticGradleExtension.jvmVersion

        if (project.plugins.hasPlugin(JavaPlugin::class.java)) {
            project.extensions.configure(JavaPluginExtension::class.java) {
                toolchain.languageVersion.set(jvmVersion)
            }

            project.tasks.withType(JavaCompile::class.java).configureEach {
                options.encoding = "UTF-8"
                options.compilerArgs.add("-Werror") // Terminate compilation if warnings occur
                options.compilerArgs.add("-Xlint:unchecked") // Warn about unchecked operations.
            }
        }

        if (project.plugins.hasPlugin(KotlinBasePlugin::class.java)) {
            project.extensions.configure(KotlinProjectExtension::class.java) {
                jvmToolchain {
                    languageVersion.set(jvmVersion)
                }
            }
        }
    }

    private fun findSpecmaticExtension(project: Project): SpecmaticGradleExtension? {
        var currentProject: Project? = project
        while (currentProject != null) {
            currentProject.extensions.findByType(SpecmaticGradleExtension::class.java)?.let {
                return it
            }
            currentProject = currentProject.parent
        }
        return null
    }

}
