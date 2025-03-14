package io.specmatic.gradle.utils

import org.eclipse.jgit.api.Git
import org.gradle.api.Project

fun gitSha(project: Project): String {
    return runCatching {
        val rootProject = project.rootProject
        Git.open(rootProject.rootDir).use {
            Git.open(rootProject.rootDir).repository.resolve("HEAD").name
        }
    }.getOrElse { "unknown - no git repo found" }
}
