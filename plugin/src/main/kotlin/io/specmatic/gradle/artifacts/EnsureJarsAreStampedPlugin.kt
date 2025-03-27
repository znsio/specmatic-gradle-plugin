package io.specmatic.gradle.artifacts

import io.specmatic.gradle.extensions.ApplicationFeature
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
                val module = extension.projectConfigurations[this.project]
                if (module is ApplicationFeature) {
                    val mainClass = module.mainClass
                    target.pluginInfo("Adding main class($mainClass) to manifest in ${this.path}")
                    manifest.attributes["Main-Class"] = mainClass
                }
                project.versionInfo().addToManifest(manifest)
            }
        }
    }

}
