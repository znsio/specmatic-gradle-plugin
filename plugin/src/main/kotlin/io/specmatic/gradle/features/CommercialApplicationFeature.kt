package io.specmatic.gradle.features

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.jar.publishing.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

class CommercialApplicationFeature(project: Project) : ApplicationFeature, ShadowingFeature, ObfuscationFeature,
    GithubReleaseFeature, DockerBuildFeature, BaseDistribution(project) {
    override var mainClass: String = ""
    override fun applyToProject() {
        super.applyToProject()
        setupLogging()

        if (this.isGradlePlugin) {
            return
        }

        project.plugins.withType(JavaPlugin::class.java) {
            val obfuscatedOriginalJar = project.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = project.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            val obfuscatedShadowJar =
                project.createObfuscatedShadowJar(obfuscatedOriginalJar, shadowActions, shadowPrefix, true)

            project.plugins.withType(MavenPublishPlugin::class.java) {
                project.createShadowedObfuscatedJarPublication(
                    obfuscatedShadowJar, project.name
                )
                project.createShadowedUnobfuscatedJarPublication(
                    unobfuscatedShadowJar, "${project.name}-all-debug"
                )
            }
        }
    }

    override fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        super.shadow(prefix, action)
    }

    override fun obfuscate(vararg proguardExtraArgs: String?) {
        super.obfuscate(*proguardExtraArgs)
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
