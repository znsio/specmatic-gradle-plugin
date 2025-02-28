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


data class PublicationDefinition(
    val types: Collection<PublicationType>, val action: Action<MavenPublication>?
)

open class SpecmaticGradleExtension {
    var jvmVersion: JavaLanguageVersion = JavaLanguageVersion.of(17)
        get() = field
        set(value) {
            require(value.asInt() >= 17) { "JVM version must be at least 17" }
            field = value
        }

    val licenseData: MutableList<ModuleLicenseData> = mutableListOf()

    val obfuscatedProjects: MutableMap<Project, List<String>?> = mutableMapOf()
    val shadowConfigurations: MutableMap<Project, Action<ShadowJar>?> = mutableMapOf()
    val publicationProjects: MutableMap<Project, PublicationDefinition> = mutableMapOf()

    fun licenseData(block: ModuleLicenseData.() -> Unit) {
        licenseData.add(ModuleLicenseData().apply(block))
    }

    fun shadow(project: Project, configuration: Action<ShadowJar>? = null) {
        shadowConfigurations[project] = configuration
    }

    fun obfuscate(project: Project, configuration: List<String>? = null) {
        obfuscatedProjects[project] = configuration
    }

    fun publication(
        project: Project,
        types: Collection<PublicationType> = listOf(),
        configuration: Action<MavenPublication>? = null
    ) {
        publicationProjects[project] = PublicationDefinition(types, configuration)
    }

}

class ModuleLicenseData {
    var name: String = ""
    var version: String = ""
    var projectUrl: String? = null
    var license: String = ""
    var licenseUrl: String? = null
}
