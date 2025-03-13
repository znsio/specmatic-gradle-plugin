package io.specmatic.gradle.artifacts

import io.specmatic.gradle.findSpecmaticExtension
import io.specmatic.gradle.pluginDebug
import io.specmatic.gradle.versioninfo.CaptureVersionInfo
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

class EnsureJarsAreStamped(val project: Project) {
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

        val extension = findSpecmaticExtension(project)
            ?: throw GradleException("SpecmaticGradleExtension not found in project $project")

        if (extension.projectConfigurations[project]?.applicationMainClass?.isBlank() == false) {
            pluginDebug("Adding main class to ${this.archiveFile.get().asFile}")
            manifest.attributes["Main-Class"] = extension.projectConfigurations[project]?.applicationMainClass
        }
        CaptureVersionInfo.fetchVersionInfoForProject(this.project).addToManifest(this.manifest)
    }

}
