package io.specmatic.gradle.extensions

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.net.URI


enum class PublicationType {
    // the obfuscated version of the original jar. Prefixed with `-obfuscated`. This will have the same transitive dependencies as the `original` jar.
    OBFUSCATED_ORIGINAL,

    // the original jar, but with shadowed dependencies. Prefixed with `-shadowed`. This will have not have any transitive dependencies.
    SHADOWED_ORIGINAL,

    // the obfuscated version of the original jar, but with shadowed dependencies. Prefixed with `-shadowed-obfuscated`. This will not have any transitive dependencies.
    SHADOWED_OBFUSCATED
}

interface PublishTarget

class MavenCentral : PublishTarget

class MavenInternal(val repoName: String, val url: URI) : PublishTarget

open class SpecmaticGradleExtension {
    var kotlinApiVersion: KotlinVersion = KotlinVersion.KOTLIN_1_9
    internal var publishTo = mutableListOf<PublishTarget>()

    fun publishToMavenCentral() {
        publishTo.add(MavenCentral())
    }

    fun publishTo(repoName: String, url: URI) {
        publishTo.add(MavenInternal(repoName, url))
    }

    fun publishTo(repoName: String, url: String) {
        publishTo(repoName, URI.create(url))
    }

    var jvmVersion: JavaLanguageVersion = JavaLanguageVersion.of(17)
        set(value) {
            require(value.asInt() >= 17) { "JVM version must be at least 17" }
            field = value
        }

    val licenseData: MutableList<ModuleLicenseData> = mutableListOf()
    internal val projectConfigurations: MutableMap<Project, ProjectConfiguration> = mutableMapOf()

    fun licenseData(block: ModuleLicenseData.() -> Unit) {
        licenseData.add(ModuleLicenseData().apply(block))
    }

    fun withProject(project: Project, block: ProjectConfiguration.() -> Unit) {
        val projectConfig = ProjectConfiguration().apply(block)
        projectConfigurations[project] = projectConfig
    }
}

private const val DEFAULT_PUBLICATION_NAME = "mavenJava"

class ProjectConfiguration {
    var applicationMainClass: String? = null
    internal var publicationName: String = DEFAULT_PUBLICATION_NAME
    internal var publicationEnabled = false
    internal var publicationTypes = mutableListOf<PublicationType>()
    internal var publicationConfigurations: Action<MavenPublication>? = null

    internal var proguardEnabled = false
    internal var proguardExtraArgs = mutableListOf<String?>()
    internal var shadowAction: Action<ShadowJar>? = null
    internal var shadowPrefix: String? = null
    internal var shadowApplication = false

    internal var dockerBuild = false
    internal var dockerBuildExtraArgs = mutableListOf<String?>()

    var iAmABigFatLibrary = false

    fun shadow(prefix: String? = null, action: Action<ShadowJar> = Action {}) {
        if (prefix != null) {
            // check that prefix is a valid java package name
            require(prefix.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"))) { "Invalid Java package name: $prefix" }
        }
        shadowPrefix = prefix
        shadowAction = action
    }

    fun shadowApplication(prefix: String? = null, action: Action<ShadowJar> = Action {}) {
        shadowApplication = true
        shadow(prefix, action)
    }

    fun obfuscate(vararg proguardExtraArgs: String?) {
        this.proguardEnabled = true
        this.proguardExtraArgs.addAll(proguardExtraArgs)
    }

    fun dockerBuild(vararg proguardExtraArgs: String?) {
        this.dockerBuild = true
        this.dockerBuildExtraArgs.addAll(proguardExtraArgs)
    }

    fun publish(
        name: String = DEFAULT_PUBLICATION_NAME,
        vararg publicationTypes: PublicationType,
        configuration: Action<MavenPublication>?
    ) {
        this.publicationName = name
        this.publicationEnabled = true
        this.publicationTypes.addAll(publicationTypes)
        this.publicationConfigurations = configuration
    }
}

class ModuleLicenseData {
    var name: String = ""
    var version: String = ""
    var projectUrl: String? = null
    var license: String = ""
    var licenseUrl: String? = null
}
