package io.specmatic.gradle.docker

import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.vuln.createDockerVulnScanTask
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import java.text.SimpleDateFormat
import java.util.*

internal fun Project.registerDockerTasks(providedImageName: String?, vararg dockerBuildArgs: String?) {
    val imageName = if (providedImageName.isNullOrEmpty()) {
        this.name
    } else {
        providedImageName
    }

    pluginInfo("Adding docker tasks on $this")

    val annotations = listOf(
        "--annotation",
        "org.opencontainers.image.created=${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(Date())}",
        "--annotation",
        "org.opencontainers.image.authors=Specmatic Team <info@specmatic.io>",
        "--annotation",
        "org.opencontainers.image.url=https://hub.docker.com/u/znsio/${imageName}",
        "--annotation",
        "org.opencontainers.image.version=${this.version}",
        "--annotation",
        "org.opencontainers.image.vendor=specmatic.io",
    )

    val dockerTags = listOf(
        "znsio/${imageName}:${this.version}",
        "znsio/${imageName}:latest"
    )

    this.tasks.register("dockerBuild", Exec::class.java) {
        this.dependsOn("assemble")
        this.group = "docker"
        this.description = "Builds the docker image"
        this.workingDir = rootProject.projectDir

        this.commandLine(
            "docker",
            "build",
            *annotations.toTypedArray(),
            "--build-arg",
            "VERSION=${this.project.version}",
            *dockerTags.flatMap { listOf("-t", it) }.toTypedArray(),
            "."
        )
        this.args(dockerBuildArgs.filterNotNull())
    }

    createDockerVulnScanTask(dockerTags.first())

    this.tasks.register("dockerBuildxPublish", Exec::class.java) {
        this.dependsOn("assemble")
        this.group = "docker"
        this.description = "Publishes the multivariant docker image"
        this.workingDir = rootProject.projectDir

        this.commandLine(
            "docker",
            "buildx",
            "build",
            *annotations.toTypedArray(),
            "--platform",
            "linux/amd64,linux/arm64",
            "--build-arg",
            "VERSION=${this.project.version}",
            "--push",
            *dockerTags.flatMap { listOf("-t", it) }.toTypedArray(),
            "."
        )
        this.args(dockerBuildArgs.filterNotNull())
    }
}
