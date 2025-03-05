package io.specmatic.gradle

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

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
        ConfigureVersionInfo.fetchVersionInfoForProject(this.project).addToManifest(this.manifest)
    }

}