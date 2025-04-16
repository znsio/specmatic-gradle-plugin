package io.specmatic.gradle.features

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.jar.publishing.createShadowedUnobfuscatedJarPublication
import io.specmatic.gradle.jar.publishing.createUnobfuscatedJarPublication
import io.specmatic.gradle.jar.publishing.createUnobfuscatedShadowJar
import io.specmatic.gradle.jar.publishing.forceJavadocAndSourcesJars
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

open class OSSApplicationAndLibraryFeature(project: Project) : ApplicationFeature, DockerBuildFeature, ShadowingFeature,
    GithubReleaseFeature, BaseDistribution(project) {
    override var mainClass: String = ""

    override fun applyToProject() {
        super.applyToProject()
        setupLogging()

        if (this.isGradlePlugin) {
            return
        }

        project.plugins.withType(JavaPlugin::class.java) {
            project.forceJavadocAndSourcesJars()
            val unobfuscatedShadowJarTask = project.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            project.plugins.withType(MavenPublishPlugin::class.java) {

                project.createUnobfuscatedJarPublication(
                    project.name
                )

                val shadowJarPublication = project.createShadowedUnobfuscatedJarPublication(
                    unobfuscatedShadowJarTask,
                    "${project.name}-all",
                )

                shadowJarPublication.configure {
                    artifact(project.tasks.named("sourcesJar")) {
                        classifier = "sources"
                    }
                    artifact(project.tasks.named("javadocJar")) {
                        classifier = "javadoc"
                    }
                }
            }
        }
    }

    override fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        super.shadow(prefix, action)
    }

    override fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        super.githubRelease(block)
    }

    override fun dockerBuild(vararg dockerBuildArgs: String?) {
        super.dockerBuild(*dockerBuildArgs)
    }

    override fun dockerBuild(imageName: String?, vararg dockerBuildArgs: String?) {
        super.dockerBuild(imageName = imageName, *dockerBuildArgs)
    }
}
