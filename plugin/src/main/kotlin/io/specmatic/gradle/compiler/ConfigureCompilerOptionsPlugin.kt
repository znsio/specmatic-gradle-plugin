package io.specmatic.gradle.compiler

import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class ConfigureCompilerOptionsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val specmaticGradleExtension = target.specmaticExtension()

        val jvmVersion = specmaticGradleExtension.jvmVersion
        val kotlinApiVersion = specmaticGradleExtension.kotlinApiVersion

        target.plugins.withType(JavaPlugin::class.java) {
            target.pluginInfo("Configuring compiler options on $target")

            target.extensions.configure(JavaPluginExtension::class.java) {
                toolchain.languageVersion.set(jvmVersion)
            }

            target.tasks.withType(JavaCompile::class.java).configureEach {
                target.pluginInfo("Configuring compiler options for ${this.path}")
                options.encoding = "UTF-8"
                options.compilerArgs.add("-Werror") // Terminate compilation if warnings occur
                options.compilerArgs.add("-Xlint:unchecked") // Warn about unchecked operations.
            }
        }

        target.plugins.withType(org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper::class.java) {
            target.pluginInfo("Configuring Kotlin compiler options on $target")
            target.extensions.configure(KotlinJvmProjectExtension::class.java) {
                jvmToolchain {
                    languageVersion.set(jvmVersion)
                }
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(jvmVersion.asInt().toString()))
                    apiVersion.set(kotlinApiVersion)
                    languageVersion.set(kotlinApiVersion)
                }
            }
        }
    }
}
