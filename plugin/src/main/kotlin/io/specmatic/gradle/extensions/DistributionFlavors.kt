package io.specmatic.gradle.extensions

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.publishing.createObfuscatedOriginalJar
import io.specmatic.gradle.jar.publishing.createObfuscatedShadowJar
import io.specmatic.gradle.jar.publishing.createUnobfuscatedShadowJar
import io.specmatic.gradle.jar.publishing.publishJar
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get

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

    fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        githubRelease = GithubReleaseConfig().apply(block)
    }

    internal open fun applyToProject(target: Project) {
        target.plugins.apply(JavaPlugin::class.java)
//        target.applyToRootProjectOrSubprojects {
        target.configureSigning()
        target.configurePublishing(publicationConfigurations)
//        }
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
}

open class OSSLibraryConfig : BaseDistribution() {
    override fun applyToProject(target: Project) {
        super.applyToProject(target)
//        target.applyToRootProjectOrSubprojects {
        target.afterEvaluate {
            target.publishJar(publicationConfigurations, target.name, null, target.components["java"])
        }
//        }
    }
}

open class OSSApplicationConfig() : ApplicationFeature, ShadowingFeature, BaseDistribution(),
    DockerBuildFeature by DockerBuildFeatureImpl() {
    override var mainClass: String = ""

    override fun applyToProject(target: Project) {
        super.applyToProject(target)

//        target.applyToRootProjectOrSubprojects {
        target.afterEvaluate {
            val unobfuscatedShadowJarTask = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            target.publishJar(publicationConfigurations, target.name, unobfuscatedShadowJarTask)
        }
//        }
    }

    override fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        super.shadow(prefix, action)
    }
}

class CommercialLibraryConfig : ObfuscationFeature, ShadowingFeature, BaseDistribution() {
    override fun applyToProject(target: Project) {
        super.applyToProject(target)
        target.plugins.withType(JavaPlugin::class.java) {
//            target.afterEvaluate {
            val obfuscatedOriginalJar = target.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, false)
            val obfuscatedShadowJar = target.createObfuscatedShadowJar(
                obfuscatedOriginalJar, shadowActions, shadowPrefix, false
            )

            target.publishJar(publicationConfigurations, target.name, obfuscatedShadowJar)
            target.publishJar(
                publicationConfigurations,
                "${target.name}-dont-use-this-unless-you-know-what-you-are-doing",
                target.tasks.jar,
                target.components["java"]
            )
            target.publishJar(
                publicationConfigurations, "${target.name}-min", obfuscatedOriginalJar, target.components["java"]
            )
            target.publishJar(
                publicationConfigurations, "${target.name}-all-debug", unobfuscatedShadowJar
            )
        }
//        }
    }

    override fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        super.shadow(prefix, action)
    }

    override fun obfuscate(vararg proguardExtraArgs: String?) {
        super.obfuscate(*proguardExtraArgs)
    }
}


class CommercialApplicationConfig : ApplicationFeature, ShadowingFeature, ObfuscationFeature, BaseDistribution(),
    DockerBuildFeature by DockerBuildFeatureImpl() {
    override var mainClass: String = ""
    override fun applyToProject(target: Project) {
        super.applyToProject(target)

        target.plugins.withType(JavaPlugin::class.java) {
//            target.afterEvaluate {
            val obfuscatedOriginalJar = target.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            val obfuscatedShadowJar = target.createObfuscatedShadowJar(
                obfuscatedOriginalJar, shadowActions, shadowPrefix, true
            )

            target.publishJar(publicationConfigurations, target.name, obfuscatedShadowJar)
            target.publishJar(publicationConfigurations, "${target.name}-all-debug", unobfuscatedShadowJar)
        }
//        }
    }

    override fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        super.shadow(prefix, action)
    }

    override fun obfuscate(vararg proguardExtraArgs: String?) {
        super.obfuscate(*proguardExtraArgs)
    }
}

class CommercialApplicationAndLibraryConfig : ShadowingFeature, ObfuscationFeature, ApplicationFeature,
    BaseDistribution(), DockerBuildFeature by DockerBuildFeatureImpl() {
    override var mainClass: String = ""

    override fun applyToProject(target: Project) {
        super.applyToProject(target)

        target.plugins.withType(JavaPlugin::class.java) {
//        target.afterEvaluate {
            val obfuscatedOriginalJar = target.createObfuscatedOriginalJar(proguardExtraArgs)
            val unobfuscatedShadowJar = target.createUnobfuscatedShadowJar(shadowActions, shadowPrefix, true)
            val obfuscatedShadowJar =
                target.createObfuscatedShadowJar(obfuscatedOriginalJar, shadowActions, shadowPrefix, true)
            //

            target.publishJar(publicationConfigurations, target.name, obfuscatedShadowJar)
            target.publishJar(
                publicationConfigurations,
                "${target.name}-dont-use-this-unless-you-know-what-you-are-doing",
                target.tasks.jar,
                target.components["java"]
            )
            target.publishJar(
                publicationConfigurations, "${target.name}-min", obfuscatedOriginalJar, target.components["java"]
            )
            target.publishJar(publicationConfigurations, "${target.name}-all-debug", unobfuscatedShadowJar)
        }
    }

    override fun shadow(prefix: String?, action: Action<ShadowJar>?) {
        super.shadow(prefix, action)
    }

    override fun obfuscate(vararg proguardExtraArgs: String?) {
        super.obfuscate(*proguardExtraArgs)
    }
}
