package io.specmatic.gradle.dock

import io.specmatic.gradle.SpecmaticGradlePlugin
import io.specmatic.gradle.features.DockerBuildConfig
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.versioninfo.versionInfo
import io.specmatic.gradle.vuln.createDockerVulnScanTask
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import java.text.SimpleDateFormat
import java.util.*

internal fun Project.registerDockerTasks(dockerBuildConfig: DockerBuildConfig) {
    if (rootProject.file("Dockerfile").exists()) {
        pluginInfo("WARN: Dockerfile already exists in the root project. Please remove it.")
    }

    val imageName = dockerImage(dockerBuildConfig)
    val dockerFile = project.layout.buildDirectory.get().asFile.resolve("docker/Dockerfile.tmp")

    val dockerfileTask = tasks.register("createDockerFile") {
        group = "docker"
        description = "Creates a Dockerfile for the project"
        outputs.file(dockerFile)

        doFirst {
            dockerFile.also {
                it.parentFile.mkdirs()
                val templateStream =
                    SpecmaticGradlePlugin::class.java.classLoader.getResourceAsStream("Dockerfile.template")
                        ?: throw IllegalStateException("Unable to find Dockerfile.template in classpath")
                it.writeBytes(templateStream.readBytes())
            }
        }
    }


    pluginInfo("Adding docker tasks on $this")

    val annotations = annotationArgs(imageName)

    val dockerTags = arrayOf(
        "znsio/${imageName}:${this.version}", "znsio/${imageName}:latest"
    )

    val commonDockerArgs = arrayOf(
        *annotations,
        "--build-arg",
        "SOURCE_JAR=${rootProject.relativePath(dockerBuildConfig.jar!!)}",
        "--build-arg",
        "APPLICATION_NAME=${imageName}",
        *dockerTags.flatMap { listOf("--tag", it) }.toTypedArray(),
        "--file",
        rootProject.relativePath(dockerFile),
        "."
    )

    val scanLocalDockerImageTask = createDockerVulnScanTask(dockerTags.first())

    tasks.register("dockerBuild", Exec::class.java) {
        this.finalizedBy(scanLocalDockerImageTask)
        this.dependsOn("assemble", dockerfileTask)
        this.group = "docker"
        this.description = "Builds the docker image"
        this.workingDir = rootProject.projectDir

        this.commandLine(
            "docker", "build", *commonDockerArgs
        )
        this.args(dockerBuildConfig.extraDockerArgs)
    }

    this.tasks.register("dockerBuildxPublish", Exec::class.java) {
        this.dependsOn("assemble", dockerfileTask)
        this.group = "docker"
        this.description = "Publishes the multivariant docker image"
        this.workingDir = rootProject.projectDir

        this.commandLine(
            "docker",
            "buildx",
            "build",
            "--platform",
            "linux/amd64,linux/arm64",
            *commonDockerArgs,
            "--push",
        )
        this.args(dockerBuildConfig.extraDockerArgs)
    }
}

private fun Project.dockerImage(dockerBuildConfig: DockerBuildConfig): String =
    if (dockerBuildConfig.imageName.isNullOrBlank()) {
        this.name
    } else {
        dockerBuildConfig.imageName!!
    }

private fun Project.annotationArgs(imageName: String): Array<String> = arrayOf(
    "org.opencontainers.image.created=${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(Date())}",
    "org.opencontainers.image.authors=Specmatic Team <info@specmatic.io>",
    "org.opencontainers.image.url=https://hub.docker.com/u/znsio/${imageName}",
    "org.opencontainers.image.version=${this.version}",
    "org.opencontainers.image.revision=${project.versionInfo().gitCommit}",
    "org.opencontainers.image.vendor=specmatic.io",
).flatMap { listOf("--annotation", it) }.toTypedArray()
