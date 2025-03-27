package io.specmatic.gradle.dock

import io.specmatic.gradle.extensions.DockerBuildFeatureImpl
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import java.text.SimpleDateFormat
import java.util.*

class DockerPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.afterEvaluate {
            applyAfterEvaluate(target)
        }
    }

    private fun applyAfterEvaluate(target: Project) {
        val specmaticExtension = target.specmaticExtension()
        val projectConfiguration = specmaticExtension.projectConfigurations[target]
        if (projectConfiguration !is DockerBuildFeatureImpl) {
            return
        }


        target.pluginInfo("Adding docker tasks on $target")

        val annotations = listOf(
            "--annotation",
            "org.opencontainers.image.created=${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(Date())}",
            "--annotation",
            "org.opencontainers.image.authors=Specmatic Team <info@specmatic.io>",
            "--annotation",
            "org.opencontainers.image.url=https://hub.docker.com/u/znsio/${target.name}",
            "--annotation",
            "org.opencontainers.image.version=${target.version}",
            "--annotation",
            "org.opencontainers.image.vendor=specmatic.io",
        )

        target.tasks.register("dockerBuild", Exec::class.java) {
            dependsOn("assemble")
            group = "docker"
            description = "Builds the docker image"
            commandLine(
                "docker",
                "build",
                *annotations.toTypedArray(),
                "--build-arg",
                "VERSION=${target.version}",
                "--no-cache",
                "-t",
                "znsio/${target.name}:${target.version}",
                "-t",
                "znsio/${target.name}:latest",
                "."
            )
            args(projectConfiguration.dockerBuildExtraArgs.filterNotNull())
        }

        target.tasks.register("dockerBuildxPublish", Exec::class.java) {
            dependsOn("assemble")
            group = "docker"
            description = "Publishes the multivariant docker image"

            commandLine(
                "docker",
                "buildx",
                "build",
                *annotations.toTypedArray(),
                "--platform",
                "linux/amd64,linux/arm64",
                "--build-arg",
                "VERSION=${target.version}",
                "--push",
                "-t",
                "znsio/${target.name}:${target.version}",
                "-t",
                "znsio/${target.name}:latest",
                "."
            )
            args(projectConfiguration.dockerBuildExtraArgs.filterNotNull())
        }
    }
}
