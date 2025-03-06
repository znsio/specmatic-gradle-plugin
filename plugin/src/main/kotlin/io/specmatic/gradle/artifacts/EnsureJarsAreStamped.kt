package io.specmatic.gradle.artifacts

import io.specmatic.gradle.pluginDebug
import io.specmatic.gradle.versioninfo.CaptureVersionInfo
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
        pluginDebug("Ensuring that ${this.path} is stamped")
        CaptureVersionInfo.fetchVersionInfoForProject(this.project).addToManifest(this.manifest)
    }

}