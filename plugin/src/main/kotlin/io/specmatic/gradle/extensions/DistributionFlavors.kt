package io.specmatic.gradle.extensions

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.dock.registerDockerTasks
import io.specmatic.gradle.jar.massage.mavenPublications
import io.specmatic.gradle.jar.publishing.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

// just a marker interface
interface DistributionFlavor

abstract class BaseDistribution(protected val project: Project) : DistributionFlavor {
    internal var isGradlePlugin = false
    internal var publicationConfigurations = mutableListOf<Action<MavenPublication>>()
    internal var githubRelease: GithubReleaseConfig = GithubReleaseConfig()
    internal var shadowActions = mutableListOf<Action<ShadowJar>>()
    internal var proguardExtraArgs = mutableListOf<String?>()
    internal var shadowPrefix = ""

    fun publish(configuration: Action<MavenPublication>) {
        this.publicationConfigurations.add(configuration)
    }

    fun publishGradle(configuration: Action<MavenPublication>) {
        this.isGradlePlugin = true
        this.publicationConfigurations.add(configuration)
    }

    internal open fun applyToProject() {
        // hook for any common setup
        project.plugins.apply(JavaPlugin::class.java)
        project.plugins.withType(MavenPublishPlugin::class.java) {
            project.mavenPublications {
                publicationConfigurations.forEach {
                    it.execute(this)
                }
            }
        }
    }

    protected open fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        if (prefix != null) {
            // check that prefix is a valid java package name
            require(prefix.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"))) { "Invalid Java package name: $prefix" }
        }
        shadowPrefix = prefix.orEmpty().trim()
        if (action != null) {
            shadowActions.add(action)
        }
    }

    protected open fun obfuscate(vararg proguardExtraArgs: String?) {
        this.proguardExtraArgs.addAll(proguardExtraArgs)
    }

    protected open fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        githubRelease = GithubReleaseConfig().apply(block)
    }

    protected open fun dockerBuild(vararg dockerBuildArgs: String?) {
        this.project.registerDockerTasks(*dockerBuildArgs)
    }
}

open class OSSLibraryConfig(project: Project) : GithubReleaseFeature, BaseDistribution(project) {
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
        }
    }

    override fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        super.githubRelease(block)
    }
}

open class OSSApplicationConfig(project: Project) : ApplicationFeature, DockerBuildFeature, ShadowingFeature,
    GithubReleaseFeature,
    BaseDistribution(project) {
    override var mainClass: String = ""

    override fun applyToProject() {
        super.applyToProject()
        if (this.isGradlePlugin) {
            return
        }

        project.plugins.withType(JavaPlugin::class.java) {
            project.forceJavadocAndSourcesJars()
            val unobfuscatedShadowJarTask = project.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            project.plugins.withType(MavenPublishPlugin::class.java) {
                project.createShadowedUnobfuscatedJarPublication(
                    unobfuscatedShadowJarTask,
                    project.name,
                )

                project.mavenPublications {
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
}

open class OSSApplicationLibraryConfig(project: Project) : ApplicationFeature, DockerBuildFeature, ShadowingFeature,
    GithubReleaseFeature,
    BaseDistribution(project) {
    override var mainClass: String = ""

    override fun applyToProject() {
        super.applyToProject()
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
}


class CommercialLibraryConfig(project: Project) : ObfuscationFeature, ShadowingFeature,
    GithubReleaseFeature, BaseDistribution(project) {
    override fun applyToProject() {
        super.applyToProject()
        if (this.isGradlePlugin) {
            return
        }

        project.plugins.withType(JavaPlugin::class.java) {
            val obfuscatedOriginalJar = project.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = project.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, false)
            val obfuscatedShadowJar = project.createObfuscatedShadowJar(
                obfuscatedOriginalJar, shadowActions, shadowPrefix, false
            )
            project.plugins.withType(MavenPublishPlugin::class.java) {
                project.createUnobfuscatedJarPublication(
                    "${project.name}-dont-use-this-unless-you-know-what-you-are-doing"
                )
                project.createObfuscatedOriginalJarPublication(
                    obfuscatedOriginalJar, "${project.name}-min"
                )
                project.createShadowedUnobfuscatedJarPublication(
                    unobfuscatedShadowJar, "${project.name}-all-debug"
                )
                project.createShadowedObfuscatedJarPublication(
                    obfuscatedShadowJar, project.name
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
}


class CommercialApplicationConfig(project: Project) : ApplicationFeature, ShadowingFeature, ObfuscationFeature,
    GithubReleaseFeature,
    DockerBuildFeature,
    BaseDistribution(project) {
    override var mainClass: String = ""
    override fun applyToProject() {
        super.applyToProject()
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
}

class CommercialApplicationAndLibraryConfig(project: Project) : ShadowingFeature, ObfuscationFeature,
    ApplicationFeature,
    GithubReleaseFeature,
    DockerBuildFeature,
    BaseDistribution(project) {
    override var mainClass: String = ""

    override fun applyToProject() {
        super.applyToProject()
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
                project.createObfuscatedOriginalJarPublication(
                    obfuscatedOriginalJar, "${project.name}-min"
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
}
