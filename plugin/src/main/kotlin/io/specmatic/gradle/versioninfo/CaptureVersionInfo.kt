package io.specmatic.gradle.versioninfo

import io.specmatic.gradle.utils.gitSha
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra

fun Project.versionInfo(): ProjectVersionInfo = ProjectVersionInfo(
    version = this.version.toString(),
    gitCommit = project.gitSha(),
    group = this.group.toString(),
    name = this.name,
    kotlinPackageName = kotlinPackage(),
    isRootProject = this == this.rootProject,
    timestamp = project.maybeBuildTimestampIfEnabled(),
)

private fun Project.maybeBuildTimestampIfEnabled(): String? {
    val shouldTimestampJars = project.hasProperty("specmatic.jar.timestamp")

    if (shouldTimestampJars) {
        val now = ZonedDateTime.now()
        val timestamp = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        this.rootProject.extra.set("specmaticPluginBuildTime", timestamp)
        return timestamp
    } else {
        return null
    }
}

private fun Project.gitSha(): String {
    if (!rootProject.extra.has("specmaticPluginGitSha")) {
        rootProject.extra.set("specmaticPluginGitSha", gitSha(this))
    }
    return rootProject.extra["specmaticPluginGitSha"] as String
}

// private fun hasTimestamp(project: Project) = project.hasProperty("specmatic.jar.timestamp")
