package io.specmatic.gradle.versioninfo

import org.eclipse.jgit.api.Git
import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CaptureVersionInfo(project: Project) {
    init {

        project.rootProject.extra.set("specmaticPluginGitSha", gitSha(project))
        if (hasTimestamp(project)) {
            val now = LocalDateTime.now()
            val timestamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            project.rootProject.extra.set("specmaticPluginBuildTime", timestamp)
        }
    }

    companion object {
        fun fetchVersionInfoForProject(project: Project): ProjectVersionInfo {
            val timestamp =
                project.rootProject.extra.properties.getOrElse("specmaticPluginBuildTime") { null } as String?


            return ProjectVersionInfo(
                project.version.toString(),
                project.rootProject.extra["specmaticPluginGitSha"] as String,
                project.group.toString(),
                project.name,
                timestamp
            )
        }

        private fun hasTimestamp(project: Project) = project.hasProperty("specmatic.jar.timestamp")
    }
}

fun gitSha(project: Project): String {
    return runCatching {
        val rootProject = project.rootProject
        Git.open(rootProject.rootDir).use {
            Git.open(rootProject.rootDir).repository.resolve("HEAD").name
        }
    }.getOrElse { "unknown - no git repo found" }
}
