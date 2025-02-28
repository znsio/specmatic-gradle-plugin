package io.specmatic.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

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

        project.pluginManager.withPlugin("java") {
            println("Configuring compiler options on $project")

            project.extensions.configure(JavaPluginExtension::class.java) {
                toolchain.languageVersion.set(jvmVersion)
            }

            project.tasks.withType(JavaCompile::class.java).configureEach {
                println("Configuring compiler options for ${this.path}")
                options.encoding = "UTF-8"
                options.compilerArgs.add("-Werror") // Terminate compilation if warnings occur
                options.compilerArgs.add("-Xlint:unchecked") // Warn about unchecked operations.
            }
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            println("Configuring Kotlin compiler options on $project")
            project.extensions.configure(KotlinProjectExtension::class.java) {
                jvmToolchain {
                    languageVersion.set(jvmVersion)
                }
            }
        }
    }

}
