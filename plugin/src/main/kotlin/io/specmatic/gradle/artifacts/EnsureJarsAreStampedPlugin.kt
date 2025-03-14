package io.specmatic.gradle.artifacts

import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import io.specmatic.gradle.versioninfo.versionInfo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

class EnsureJarsAreStampedPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.afterEvaluate {
            target.tasks.withType(Jar::class.java) {
                target.pluginInfo("Ensuring that ${this.path} is stamped")
                val extension = this.project.specmaticExtension()
                val applicationMainClass = extension.projectConfigurations[this.project]?.applicationMainClass
                if (!applicationMainClass.isNullOrBlank()) {
                    target.pluginInfo("Adding main class($applicationMainClass) to manifest")
                    manifest.attributes["Main-Class"] = applicationMainClass
                }

                project.versionInfo().addToManifest(manifest)
            }
        }
    }

}
