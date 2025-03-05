package io.specmatic.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the

class ConfigureVersionFiles(project: Project) {
    init {
        project.afterEvaluate { allprojects(::createVersionInfoClass) }
    }

    private fun createVersionInfoClass(project: Project) {
        project.pluginManager.withPlugin("java") {
            println("Configuring version properties file on $project")
            val generatedKotlinSourcesDir = project.file("src/main/gen-kt")
            val generatedResourcesDir = project.file("src/main/gen-resources")

            project.tasks.named("clean", Delete::class.java) {
                delete(generatedKotlinSourcesDir)
                delete(generatedResourcesDir)
            }

            val createVersionInfoKotlinTask = project.tasks.register("createVersionInfoKotlin") {
                group = "build"

                val versionInfoForProject = ConfigureVersionInfo.fetchVersionInfoForProject(project)
                inputs.property("projectVersion", versionInfoForProject.toString())

                outputs.dir(generatedKotlinSourcesDir)

                doLast {
                    val versionInfoKotlinFile =
                        project.file("${generatedKotlinSourcesDir}/${versionInfoForProject.kotlinFilePath()}")
                    versionInfoKotlinFile.parentFile.mkdirs()
                    println("Writing version info class to $versionInfoKotlinFile")
                    versionInfoKotlinFile.writeText(versionInfoForProject.toKotlinClass())

                }
            }

            val createVersionPropertiesFileTask = project.tasks.register("createVersionPropertiesFile") {
                group = "build"

                val versionInfoForProject = ConfigureVersionInfo.fetchVersionInfoForProject(project)
                inputs.property("projectVersion", versionInfoForProject.toString())

                outputs.dir(generatedResourcesDir)

                doLast {
                    val versionInfoPropertiesFile =
                        project.file("${generatedResourcesDir}/${versionInfoForProject.propertiesFilePath()}")
                    versionInfoPropertiesFile.parentFile.mkdirs()
                    println("Writing version info properties to $versionInfoPropertiesFile")
                    versionInfoPropertiesFile.writeText(versionInfoForProject.toPropertiesFile())
                }
            }

            project.the<SourceSetContainer>()["main"].java.srcDir(createVersionInfoKotlinTask.get().outputs.files)
            project.the<SourceSetContainer>()["main"].resources.srcDir(createVersionPropertiesFileTask.get().outputs.files)
        }
    }
}