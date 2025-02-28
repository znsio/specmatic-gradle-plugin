package io.specmatic.gradle

import org.eclipse.jgit.api.Git
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EnsureJarsAreStamped(project: Project) {
    init {
        project.allprojects.forEach(::ensureJarsAreStamped)
    }

    fun ensureJarsAreStamped(project: Project) {
        project.afterEvaluate {
            project.tasks.withType(Jar::class.java) {
                configureJar()
            }

            project.tasks.whenObjectAdded {
                if (this is Jar) {
                    configureJar()
                }
            }
        }
    }

    private fun Jar.configureJar() {
        println("Ensuring that ${this.path} is stamped")

        if (this.project.hasProperty("specmatic.jar.timestamp")) {
            val now = LocalDateTime.now()
            manifest.attributes["x-specmatic-compile-timestamp"] = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
        manifest.attributes["x-specmatic-version"] = this.project.version.toString()
        manifest.attributes["x-specmatic-group"] = this.project.group.toString()
        manifest.attributes["x-specmatic-name"] = this.project.name.toString()
        manifest.attributes["x-specmatic-git-sha"] = runCatching {
            Git.open(this.project.rootProject.rootDir).use {
                Git.open(this.project.rootProject.rootDir).repository.resolve("HEAD").name
            }
        }.getOrElse { "unknown - no git repo found" }
    }
}