package io.specmatic.gradle.extensions

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.toolchain.JavaLanguageVersion


enum class PublicationType {
    // the obfuscated version of the original jar. Prefixed with `-obfuscated`. This will have the same transitive dependencies as the `original` jar.
    OBFUSCATED_ORIGINAL,

    // the original jar, but with shadowed dependencies. Prefixed with `-shadowed`. This will have not have any transitive dependencies.
    SHADOWED_ORIGINAL,

    // the obfuscated version of the original jar, but with shadowed dependencies. Prefixed with `-shadowed-obfuscated`. This will not have any transitive dependencies.
    SHADOWED_OBFUSCATED
}

open class SpecmaticGradleExtension {
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

class ProjectConfiguration {
    internal var publicationName: String = "mavenJava"
    internal var publicationEnabled = false
    internal var publicationTypes = mutableListOf<PublicationType>()
    internal var publicationConfigurations: Action<MavenPublication>? = null

    internal var proguardEnabled = false
    internal var proguardExtraArgs = mutableListOf<String>()
    internal var shadowAction: Action<ShadowJar>? = null

    fun shadow(action: Action<ShadowJar> = Action {}) {
        shadowAction = action
    }

    fun obfuscate(proguardExtraArgs: List<String>? = emptyList()) {
        this.proguardEnabled = true
        this.proguardExtraArgs.addAll(proguardExtraArgs.orEmpty())
    }

    fun publish(vararg publicationTypes: PublicationType, configuration: Action<MavenPublication>) {
        this.publicationEnabled = true
        this.publicationTypes.addAll(publicationTypes)
        this.publicationConfigurations = configuration
    }

    fun publishWithName(
        name: String,
        vararg publicationTypes: PublicationType,
        configuration: Action<MavenPublication>
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
