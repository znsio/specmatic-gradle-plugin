package io.specmatic.gradle.dock

import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.findSpecmaticExtension
import io.specmatic.gradle.pluginDebug
import org.gradle.api.Project
import org.gradle.api.tasks.Exec

class ConfigureDockerTasks(val project: Project) {
    init {
        project.afterEvaluate {
            val specmaticGradleExtension =
                findSpecmaticExtension(project) ?: throw RuntimeException("Specmatic extension not found")

            specmaticGradleExtension.projectConfigurations.forEach { project, config ->
                if (config.dockerBuild) {
                    addDockerTasks(project, config)
                }
            }
        }
    }

    private fun addDockerTasks(project: Project, config: ProjectConfiguration) {
        pluginDebug("Adding docker tasks on $project")

        project.tasks.register("dockerBuild", Exec::class.java) {
            dependsOn("shadowObfuscatedJar")
            group = "docker"
            description = "Builds the docker image"
            commandLine(
                "docker",
                "build",
                "--build-arg",
                "VERSION=${project.version}",
                "--no-cache",
                "-t",
                "znsio/${project.name}:${project.version}",
                "-t",
                "znsio/${project.name}:latest",
                "."
            )
            args(config.dockerBuildExtraArgs.filterNotNull())
        }

        project.tasks.register("dockerBuildxPublish", Exec::class.java) {
            dependsOn("shadowObfuscatedJar")

            group = "docker"
            description = "Publishes the multivariant docker image"

            commandLine(
                "docker",
                "buildx",
                "build",
                "--platform",
                "linux/amd64,linux/arm64",
                "--build-arg",
                "VERSION=${project.version}",
                "--push",
                "-t",
                "znsio/${project.name}:${project.version}",
                "-t",
                "znsio/${project.name}:latest",
                "."
            )
            args(config.dockerBuildExtraArgs.filterNotNull())
        }
    }
}
