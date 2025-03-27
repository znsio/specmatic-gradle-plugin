package io.specmatic.gradle.extensions

import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.net.URI

interface PublishTarget

class MavenCentral : PublishTarget

class MavenInternal(val repoName: String, val url: URI) : PublishTarget

open class SpecmaticGradleExtension {
    var jvmVersion: JavaLanguageVersion = JavaLanguageVersion.of(17)
        set(value) {
            require(value.asInt() >= 17) { "JVM version must be at least 17" }
            field = value
        }

    var kotlinVersion = "1.9.25"

    var kotlinApiVersion: KotlinVersion = KotlinVersion.KOTLIN_1_9
    internal val publishTo = mutableListOf<PublishTarget>()
    internal val licenseData = mutableListOf<ModuleLicenseData>()
    internal val projectConfigurations: MutableMap<Project, DistributionFlavor> = mutableMapOf()

    fun publishToMavenCentral() {
        publishTo.add(MavenCentral())
    }

    fun publishTo(repoName: String, url: URI) {
        publishTo.add(MavenInternal(repoName, url))
    }

    fun publishTo(repoName: String, url: String) {
        publishTo(repoName, URI.create(url))
    }


    fun licenseData(block: ModuleLicenseData.() -> Unit) {
        licenseData.add(ModuleLicenseData().apply(block))
    }

    fun withOSSApplication(project: Project, block: OSSApplicationConfig.() -> Unit) {
        val projectConfig = OSSApplicationConfig().apply(block)
        projectConfig.applyToProject(project)
        projectConfigurations[project] = projectConfig
    }

    fun withOSSLibrary(project: Project, block: OSSLibraryConfig.() -> Unit) {
        val projectConfig = OSSLibraryConfig().apply(block)
        projectConfig.applyToProject(project)
        projectConfigurations[project] = projectConfig
    }

    fun withCommercialApplication(project: Project, block: CommercialApplicationConfig.() -> Unit) {
        val projectConfig = CommercialApplicationConfig().apply(block)
        projectConfig.applyToProject(project)
        projectConfigurations[project] = projectConfig
    }

    fun withCommercialApplicationLibrary(project: Project, block: CommercialApplicationAndLibraryConfig.() -> Unit) {
        val projectConfig = CommercialApplicationAndLibraryConfig().apply(block)
        projectConfig.applyToProject(project)
        projectConfigurations[project] = projectConfig
    }

    fun withCommercialLibrary(project: Project, block: CommercialLibraryConfig.() -> Unit) {
        val projectConfig = CommercialLibraryConfig().apply(block)
        projectConfig.applyToProject(project)
        projectConfigurations[project] = projectConfig
    }
}


class ModuleLicenseData {
    var name: String = ""
    var version: String = ""
    var projectUrl: String? = null
    var license: String = ""
    var licenseUrl: String? = null
}
