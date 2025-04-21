package io.specmatic.gradle.features

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.dock.registerDockerTasks
import io.specmatic.gradle.jar.massage.mavenPublications
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.exclude
import org.gradlex.jvm.dependency.conflict.resolution.JvmDependencyConflictResolutionPlugin
import org.gradlex.jvm.dependency.conflict.resolution.JvmDependencyConflictsExtension

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
        this.project.registerDockerTasks(null, *dockerBuildArgs)
    }

    protected open fun dockerBuild(imageName: String?, vararg dockerBuildArgs: String?) {
        this.project.registerDockerTasks(imageName, *dockerBuildArgs)
    }

    protected fun setupLogging() {
        project.pluginInfo("Setting up logging on $project")
        project.configurations.named("implementation") {
            // add logback classic
            dependencies.add(project.dependencies.create("ch.qos.logback:logback-classic:1.5.18"))
            dependencies.add(project.dependencies.create("ch.qos.logback:logback-core:1.5.18"))

            // add bridges to redirect all other logging frameworks to SLF4J
            dependencies.add(project.dependencies.create("org.apache.logging.log4j:log4j-to-slf4j:2.24.3"))
            dependencies.add(project.dependencies.create("org.slf4j:jul-to-slf4j:2.0.17"))
            dependencies.add(project.dependencies.create("org.slf4j:jcl-over-slf4j:2.0.17"))
            dependencies.add(project.dependencies.create("org.slf4j:log4j-over-slf4j:2.0.17"))

            // exclude the logging frameworks from any transitive dependencies
            exclude(group = "org.apache.logging.log4j", module = "log4j-core")
            exclude(group = "log4j", module = "log4j")
            exclude(group = "commons-logging", module = "commons-logging")
        }

        project.plugins.withType(JvmDependencyConflictResolutionPlugin::class.java) {
            project.extensions.configure(JvmDependencyConflictsExtension::class.java) {
                logging {
                    enforceLogback("implementation")
                }
            }
        }

    }
}
