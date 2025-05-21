package io.specmatic.gradle.docker

import io.specmatic.gradle.SpecmaticGradlePlugin
import io.specmatic.gradle.features.DockerBuildConfig
import io.specmatic.gradle.features.mainJar
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.versioninfo.versionInfo
import io.specmatic.gradle.vuln.createDockerVulnScanTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import java.text.SimpleDateFormat
import java.util.*

private const val dockerOrganization = "znsio"

internal fun Project.registerDockerTasks(dockerBuildConfig: DockerBuildConfig) {
    val imageName = dockerImage(dockerBuildConfig)

    val createDockerfilesTask = tasks.register("createDockerFiles") {
        dependsOn(dockerBuildConfig.mainJarTaskName!!)
        group = "docker"
        description = "Creates the Dockerfile and other files needed to build the docker image"
        val targetJarPath = "/usr/local/share/${project.dockerImage(dockerBuildConfig)}.jar"
        createDockerfile(dockerBuildConfig, targetJarPath)
        createSpecmaticShellScript(dockerBuildConfig, targetJarPath)
    }

    pluginInfo("Adding docker tasks on $this")

    val dockerTags = listOf(
        "$dockerOrganization/$imageName:$version", "$dockerOrganization/$imageName:latest"
    )

    val commonDockerBuildArgs = annotationArgs(imageName) + dockerTags.flatMap { listOf("--tag", it) }
        .toTypedArray() +
            arrayOf("--file", "Dockerfile") +
            dockerBuildConfig.extraDockerArgs

    tasks.register("dockerBuild", Exec::class.java) {
        dependsOn(createDockerfilesTask, dockerBuildConfig.mainJarTaskName!!)
        group = "docker"
        description = "Builds the docker image"

        commandLine(
            "docker", "build", *commonDockerBuildArgs, "."
        )

        workingDir = project.layout.buildDirectory.get().asFile
    }

    createDockerVulnScanTask(dockerTags.first())

    tasks.register("dockerBuildxPublish", Exec::class.java) {
        dependsOn(createDockerfilesTask, dockerBuildConfig.mainJarTaskName!!)
        group = "docker"
        description = "Publishes the multivariant docker image"

        commandLine(
            "docker", "buildx", "build", *commonDockerBuildArgs, "--platform", "linux/amd64,linux/arm64", "--push", "."
        )

        workingDir = project.layout.buildDirectory.get().asFile
    }
}

fun Task.createSpecmaticShellScript(dockerBuildConfig: DockerBuildConfig, targetJarPath: String) {
    val imageName = project.dockerImage(dockerBuildConfig)
    val specmaticShellScript = project.layout.buildDirectory.file(imageName).get().asFile

    this.outputs.file(specmaticShellScript)

    doFirst {
        specmaticShellScript.parentFile.mkdirs()
        val templateStream = SpecmaticGradlePlugin::class.java.classLoader.getResourceAsStream("specmatic.sh.template")
            ?: throw IllegalStateException("Unable to find specmatic.sh.template in classpath")
        val templateContent = templateStream.bufferedReader().use { it.readText() }

        val shellScriptContent = templateContent.replace("%TARGET_JAR_PATH%", targetJarPath)
        specmaticShellScript.writeText(shellScriptContent)
        specmaticShellScript.setExecutable(true)
    }

}

private fun Task.createDockerfile(dockerBuildConfig: DockerBuildConfig, targetJarPath: String) {
    val dockerFile = project.layout.buildDirectory.file("Dockerfile").get().asFile

    this.outputs.file(dockerFile)

    doFirst {
        dockerFile.parentFile.mkdirs()
        val templateStream = SpecmaticGradlePlugin::class.java.classLoader.getResourceAsStream("Dockerfile")
            ?: throw IllegalStateException("Unable to find Dockerfile in classpath")
        val templateContent = templateStream.bufferedReader().use { it.readText() }

        val sourceJarPath = project.mainJar(dockerBuildConfig.mainJarTaskName!!)
            .relativeTo(project.layout.buildDirectory.get().asFile).path

        val dockerFileContent =
            templateContent.replace("%TARGET_JAR_PATH%", targetJarPath)
                .replace("%SOURCE_JAR_PATH%", sourceJarPath)
                .replace("%IMAGE_NAME%", project.dockerImage(dockerBuildConfig))

        dockerFile.writeText(dockerFileContent)
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
    "org.opencontainers.image.url=https://hub.docker.com/u/$dockerOrganization/$imageName",
    "org.opencontainers.image.version=$version",
    "org.opencontainers.image.revision=${project.versionInfo().gitCommit}",
    "org.opencontainers.image.vendor=specmatic.io",
).flatMap { listOf("--annotation", it) }.toTypedArray()
