package io.specmatic.gradle.extensions

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.jar.publishing.*
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

// just a marker interface
interface DistributionFlavor

abstract class BaseDistribution : DistributionFlavor {
    internal var publicationConfigurations = mutableListOf<Action<MavenPublication>>()
    internal var githubRelease: GithubReleaseConfig = GithubReleaseConfig()
    internal var shadowActions = mutableListOf<Action<ShadowJar>>()
    internal var proguardExtraArgs = mutableListOf<String?>()
    internal var shadowPrefix = ""

    fun publish(configuration: Action<MavenPublication>) {
        this.publicationConfigurations.add(configuration)
    }


    internal open fun applyToProject(target: Project) {
        target.plugins.apply(JavaPlugin::class.java)
        target.plugins.withType(JavaPlugin::class.java) {
            target.configurations.named("implementation") {
                this.dependencies.add(target.dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:${target.specmaticExtension().kotlinVersion}"))
            }
        }
        target.configureSigning()
        target.configurePublishing()
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

}

open class OSSLibraryConfig : GithubReleaseFeature, BaseDistribution() {
    override fun applyToProject(target: Project) {
        super.applyToProject(target)
        target.plugins.withType(JavaPlugin::class.java) {
            target.plugins.withType(MavenPublishPlugin::class.java) {
                target.createUnobfuscatedJarPublication(publicationConfigurations, target.name)
            }
        }
    }

    override fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        super.githubRelease(block)
    }
}

open class OSSApplicationConfig() : ApplicationFeature, ShadowingFeature, GithubReleaseFeature, BaseDistribution(),
    DockerBuildFeature by DockerBuildFeatureImpl() {
    override var mainClass: String = ""

    override fun applyToProject(target: Project) {
        super.applyToProject(target)

        target.plugins.withType(JavaPlugin::class.java) {
            val unobfuscatedShadowJarTask = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            target.plugins.withType(MavenPublishPlugin::class.java) {
                target.createShadowedUnobfuscatedJarPublication(
                    publicationConfigurations,
                    unobfuscatedShadowJarTask,
                    target.name,
                )
            }
        }
    }

    override fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        super.shadow(prefix, action)
    }

    override fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        super.githubRelease(block)
    }
}

class CommercialLibraryConfig : ObfuscationFeature, ShadowingFeature, GithubReleaseFeature, BaseDistribution() {
    override fun applyToProject(target: Project) {
        super.applyToProject(target)
        target.plugins.withType(JavaPlugin::class.java) {
            val obfuscatedOriginalJar = target.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, false)
            val obfuscatedShadowJar = target.createObfuscatedShadowJar(
                obfuscatedOriginalJar, shadowActions, shadowPrefix, false
            )
            target.plugins.withType(MavenPublishPlugin::class.java) {
                target.createUnobfuscatedJarPublication(
                    publicationConfigurations, "${target.name}-dont-use-this-unless-you-know-what-you-are-doing"
                )
                target.createObfuscatedOriginalJarPublication(
                    publicationConfigurations, obfuscatedOriginalJar, "${target.name}-min"
                )
                target.createShadowedUnobfuscatedJarPublication(
                    publicationConfigurations, unobfuscatedShadowJar, "${target.name}-all-debug"
                )
                target.createShadowedObfuscatedJarPublication(
                    publicationConfigurations, obfuscatedShadowJar, target.name
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


class CommercialApplicationConfig : ApplicationFeature, ShadowingFeature, ObfuscationFeature, GithubReleaseFeature,
    BaseDistribution(),
    DockerBuildFeature by DockerBuildFeatureImpl() {
    override var mainClass: String = ""
    override fun applyToProject(target: Project) {
        super.applyToProject(target)

        target.plugins.withType(JavaPlugin::class.java) {
            val obfuscatedOriginalJar = target.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            val obfuscatedShadowJar =
                target.createObfuscatedShadowJar(obfuscatedOriginalJar, shadowActions, shadowPrefix, true)

            target.plugins.withType(MavenPublishPlugin::class.java) {
                target.createShadowedObfuscatedJarPublication(
                    publicationConfigurations, obfuscatedShadowJar, target.name
                )
                target.createShadowedUnobfuscatedJarPublication(
                    publicationConfigurations, unobfuscatedShadowJar, "${target.name}-all-debug"
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

class CommercialApplicationAndLibraryConfig : ShadowingFeature, ObfuscationFeature, ApplicationFeature,
    GithubReleaseFeature,
    BaseDistribution(), DockerBuildFeature by DockerBuildFeatureImpl() {
    override var mainClass: String = ""

    override fun applyToProject(target: Project) {
        super.applyToProject(target)

        target.plugins.withType(JavaPlugin::class.java) {
            val obfuscatedOriginalJar = target.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            val obfuscatedShadowJar =
                target.createObfuscatedShadowJar(obfuscatedOriginalJar, shadowActions, shadowPrefix, true)

            target.plugins.withType(MavenPublishPlugin::class.java) {
                target.createShadowedObfuscatedJarPublication(
                    publicationConfigurations, obfuscatedShadowJar, target.name
                )
                target.createShadowedUnobfuscatedJarPublication(
                    publicationConfigurations, unobfuscatedShadowJar, "${target.name}-all-debug"
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
