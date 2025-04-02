package io.specmatic.gradle.versioninfo

import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class VersionInfoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType(JavaPlugin::class.java) {
            if (project.group.toString().isBlank()) {
                throw GradleException("Set your project group in the `gradle.properties`, not in the `build.gradle.kts` file")
            }

            if (project.version.toString().isEmpty()) {
                throw GradleException("Set your project version in the `gradle.properties`, not in the `build.gradle.kts` file")
            }

            project.pluginInfo("Configuring version properties file")

            val versionInfoForProject = project.versionInfo()

            val generatedKotlinSourcesDir = project.file("src/main/gen-kt")
            val generatedResourcesDir = project.file("src/main/gen-resources")
            val versionInfoKotlinFile = generatedKotlinSourcesDir.resolve(versionInfoForProject.kotlinFilePath())
            val versionInfoPropertiesFile = generatedResourcesDir.resolve(versionInfoForProject.propertiesFilePath())

            project.tasks.named("clean", Delete::class.java) {
                delete(generatedKotlinSourcesDir)
                delete(generatedResourcesDir)
            }

            val createVersionInfoKotlinTask = project.tasks.register("createVersionInfoKotlin") {
                group = "build"

                inputs.property("projectVersion", versionInfoForProject.toString())
                outputs.dir(generatedKotlinSourcesDir)
                outputs.cacheIf { true }

                doLast {
                    versionInfoKotlinFile.parentFile.mkdirs()
                    versionInfoKotlinFile.writeText(versionInfoForProject.toKotlinClass())
                }
            }

            val createVersionPropertiesFileTask = project.tasks.register("createVersionPropertiesFile") {
                group = "build"

                inputs.property("projectVersion", versionInfoForProject.toString())
                outputs.dir(generatedResourcesDir)
                outputs.cacheIf { true }

                doLast {
                    versionInfoPropertiesFile.parentFile.mkdirs()
                    versionInfoPropertiesFile.writeText(versionInfoForProject.toPropertiesFile())
                }
            }

            // for resources
            project.tasks.withType(ProcessResources::class.java) { dependsOn(createVersionPropertiesFileTask) }

            // for any compilation, we need the VersionInfo.kt
            project.tasks.withType(JavaCompile::class.java) { dependsOn(createVersionInfoKotlinTask) }
            project.tasks.withType(KotlinCompile::class.java) { dependsOn(createVersionInfoKotlinTask) }

            // for sources jars
            project.tasks.withType(AbstractArchiveTask::class.java) {
                dependsOn(createVersionInfoKotlinTask)
                dependsOn(createVersionPropertiesFileTask)
            }

            project.the<SourceSetContainer>()["main"].java.srcDir(generatedKotlinSourcesDir)
            project.the<SourceSetContainer>()["main"].resources.srcDir(generatedResourcesDir)
        }
    }
}
