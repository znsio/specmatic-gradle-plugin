package io.specmatic.gradle.features

import io.specmatic.gradle.jar.publishing.createUnobfuscatedJarPublication
import io.specmatic.gradle.jar.publishing.forceJavadocAndSourcesJars
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.plugins.signing.Sign

open class OSSLibraryFeature(project: Project) : GithubReleaseFeature, BaseDistribution(project) {
    override fun applyToProject() {
        super.applyToProject()
        if (this.isGradlePlugin) {
            return
        }

        project.plugins.withType(JavaPlugin::class.java) {
            project.forceJavadocAndSourcesJars()

            project.plugins.withType(MavenPublishPlugin::class.java) {
                project.createUnobfuscatedJarPublication(project.name)
            }

            project.tasks.withType(Sign::class.java) {
                dependsOn(
                    project.tasks.withType(org.gradle.jvm.tasks.Jar::class.java)
                        .filter { it.name.lowercase().endsWith("sourcesjar") })
            }

            project.tasks.withType(AbstractPublishToMaven::class.java) {
                dependsOn(
                    project.tasks.withType(org.gradle.jvm.tasks.Jar::class.java)
                        .filter { it.name.lowercase().endsWith("sourcesjar") })
            }

            project.tasks.withType(GenerateModuleMetadata::class.java) {
                dependsOn(
                    project.tasks.withType(org.gradle.jvm.tasks.Jar::class.java)
                        .filter { it.name.lowercase().endsWith("sourcesjar") })
            }

        }
    }

    override fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        super.githubRelease(block)
    }
}
