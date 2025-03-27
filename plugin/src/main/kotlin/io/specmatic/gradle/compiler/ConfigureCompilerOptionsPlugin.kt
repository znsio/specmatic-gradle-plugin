package io.specmatic.gradle.compiler

import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

class ConfigureCompilerOptionsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.afterEvaluate {
            val specmaticGradleExtension = target.specmaticExtension()

            val jvmVersion = specmaticGradleExtension.jvmVersion
            val kotlinApiVersion = specmaticGradleExtension.kotlinApiVersion

            setupJavaCompilerOpts(target, jvmVersion)
            setupKotlinCompilerOpts(target, jvmVersion, kotlinApiVersion)
        }
    }

    private fun setupKotlinCompilerOpts(
        target: Project,
        jvmVersion: JavaLanguageVersion,
        kotlinApiVersion: KotlinVersion
    ) {
        target.plugins.withType(KotlinPluginWrapper::class.java) {
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

    private fun setupJavaCompilerOpts(target: Project, jvmVersion: JavaLanguageVersion) {
        target.plugins.withType(JavaPlugin::class.java) {
            target.pluginInfo("Configuring compiler options on $target")

            target.extensions.configure(JavaPluginExtension::class.java) {
                toolchain.languageVersion.set(jvmVersion)
            }

            target.tasks.withType(JavaCompile::class.java).configureEach {
                target.pluginInfo("Configuring compiler options for ${this.path}")
                options.encoding = "UTF-8"
                options.compilerArgs.add("-Xlint:unchecked") // Warn about unchecked operations.
            }
        }
    }
}
