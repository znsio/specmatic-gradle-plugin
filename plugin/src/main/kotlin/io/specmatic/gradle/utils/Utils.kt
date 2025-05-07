package io.specmatic.gradle.utils

import io.specmatic.gradle.release.GitOperations
import org.gradle.api.Project

fun gitSha(project: Project): String {
    return runCatching {
        GitOperations(project.rootDir, project.properties, project.logger).gitSha()
    }.getOrElse { "unknown - no git repo found" }
}
