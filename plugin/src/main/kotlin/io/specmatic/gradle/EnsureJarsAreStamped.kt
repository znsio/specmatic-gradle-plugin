package io.specmatic.gradle

import org.eclipse.jgit.api.Git
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

class EnsureJarsAreStamped(project: Project) {
    init {
        project.allprojects.forEach(::ensureJarsAreStamped)
    }

    fun ensureJarsAreStamped(project: Project) {
        project.afterEvaluate {
            project.tasks.withType(Jar::class.java) {
                manifest.attributes["x-specmatic-version"] = project.version.toString()
                manifest.attributes["x-specmatic-group"] = project.group.toString()
                manifest.attributes["x-specmatic-name"] = project.name.toString()
                manifest.attributes["x-specmatic-git-sha"] = runCatching {
                    Git.open(project.rootProject.rootDir).use {
                        Git.open(project.rootProject.rootDir).repository.resolve("HEAD").name
                    }
                }.getOrElse { "unknown - no git repo found" }

            }
        }
    }
}