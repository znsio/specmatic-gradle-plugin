package io.specmatic.gradle.publishing

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishPlugin
import com.vanniktech.maven.publish.SonatypeHost
import io.specmatic.gradle.extensions.PublicationDefinition
import io.specmatic.gradle.extensions.PublicationType
import io.specmatic.gradle.findSpecmaticExtension
import io.specmatic.gradle.obfuscate.OBFUSCATE_JAR_TASK
import io.specmatic.gradle.pluginDebug
import io.specmatic.gradle.shadow.SHADOW_OBFUSCATED_JAR
import io.specmatic.gradle.shadow.SHADOW_ORIGINAL_JAR
import io.specmatic.gradle.shadow.jarTaskProvider
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.signing.SigningPlugin

class ConfigurePublications(project: Project) {
    init {
        project.afterEvaluate {
            val specmaticExtension =
                findSpecmaticExtension(project) ?: throw GradleException("SpecmaticGradleExtension not found")
            val publicationProjects = specmaticExtension.publicationProjects
            publicationProjects.forEach(::configure)
        }
    }

    fun configure(project: Project, configuration: PublicationDefinition) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)

        project.pluginManager.withPlugin("com.vanniktech.maven.publish") {

            project.tasks.withType(GenerateModuleMetadata::class.java).configureEach {
                enabled = false
            }

            pluginDebug("Configuring maven publishing on ${project}")
            val stagingRepo = project.rootProject.layout.buildDirectory.dir("mvn-repo")

            val specmaticExtension =
                findSpecmaticExtension(project) ?: throw GradleException("SpecmaticGradleExtension not found")

            val publishing = project.extensions.getByType(PublishingExtension::class.java)

//            configureAnyExistingOrNewPublications(publishing, project, configuration)
            configurePublishingToStagingRepo(publishing, project, stagingRepo)

            if (!configuration.types.isEmpty()) {
                if (!specmaticExtension.obfuscatedProjects.containsKey(project) && !specmaticExtension.shadowConfigurations.containsKey(
                        project
                    )
                ) {
                    printWarning(project)
                }
            }

            configureJarPublishing(publishing, project, configuration)
            configureMavenCentralPublishing(project)
        }
    }

    private fun configureMavenCentralPublishing(project: Project) {
        project.extensions.getByType(MavenPublishBaseExtension::class.java).apply {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, false)
            signAllPublications()
        }
    }

    private fun configureJarPublishing(
        publishing: PublishingExtension, project: Project, configuration: PublicationDefinition
    ) {

        configureOriginalJarPublication(publishing, project, configuration)

        if (configuration.types.contains(PublicationType.OBFUSCATED_ORIGINAL)) {
            configureObfuscatedOriginalJarPublication(publishing, project, configuration)
        }

        if (configuration.types.contains(PublicationType.SHADOWED_ORIGINAL)) {
            configureShadowOriginalJarPublication(publishing, project, configuration)
        }

        if (configuration.types.contains(PublicationType.SHADOWED_OBFUSCATED)) {
            configureShadowObfuscatedJarPublication(publishing, project, configuration)
        }
    }

    private fun printWarning(project: Project) {
        val message =
            "ERROR. $project is both obfuscated and shadowed. Don't specify publication types in `specmatic` block for $project"
        pluginDebug()
        pluginDebug("!".repeat(message.length))
        pluginDebug(message)
        pluginDebug("ERROR. The build will fail".padEnd(message.length))
        pluginDebug("!".repeat(message.length))
        pluginDebug()
    }

    private fun configurePublishingToStagingRepo(
        publishing: PublishingExtension, project: Project, stagingRepo: Provider<Directory>
    ) {
        publishing.repositories.maven {
            name = "staging"
            url = project.uri(stagingRepo)
        }
    }

    private fun configureShadowObfuscatedJarPublication(
        publishing: PublishingExtension, project: Project, configuration: PublicationDefinition
    ) {
        val shadowObfuscatedJarTask = project.tasks.named(SHADOW_OBFUSCATED_JAR, Jar::class.java)
        publishing.publications.register(SHADOW_OBFUSCATED_JAR, MavenPublication::class.java) {
            artifact(shadowObfuscatedJarTask)
            artifactId = project.name + "-shadow-obfuscated"
            pom.packaging = "jar"

            configuration.action?.execute(this)
        }

        project.createConfigurationAndAddArtifacts(shadowObfuscatedJarTask)
    }

    private fun configureShadowOriginalJarPublication(
        publishing: PublishingExtension, project: Project, configuration: PublicationDefinition
    ) {
        val shadowOriginalJarTask = project.tasks.named(SHADOW_ORIGINAL_JAR, Jar::class.java)

        publishing.publications.register(SHADOW_ORIGINAL_JAR, MavenPublication::class.java) {
            artifact(shadowOriginalJarTask)
            artifactId = project.name + "-shadow-original"
            pom.packaging = "jar"
            configuration.action?.execute(this)
        }

        project.createConfigurationAndAddArtifacts(shadowOriginalJarTask)
    }


    private fun configureOriginalJarPublication(
        publishing: PublishingExtension, project: Project, configuration: PublicationDefinition
    ) {
        val name = "originalJar"
        publishing.publications.register(name, MavenPublication::class.java) {
            from(project.components["java"])
            artifactId = project.name + "-original"
            pom.packaging = "jar"
            configuration.action?.execute(this)
        }

        project.createConfigurationAndAddArtifacts(name, project.jarTaskProvider())
    }

    private fun configureObfuscatedOriginalJarPublication(
        publishing: PublishingExtension,
        project: Project,
        configuration: PublicationDefinition
    ) {
        val obfuscateJarTask = project.tasks.named(OBFUSCATE_JAR_TASK, Jar::class.java)
        publishing.publications.register(OBFUSCATE_JAR_TASK, MavenPublication::class.java) {
            artifact(obfuscateJarTask)
            artifactId = project.name + "-obfuscated-original"
            pom {
                packaging = "jar"
                withXml {
                    val topLevel = asNode()
                    val dependencies = topLevel.appendNode("dependencies")
                    project.configurations.getByName("compileClasspath").allDependencies.forEach {
                        val dependency = dependencies.appendNode("dependency")
                        dependency.appendNode("groupId", it.group)
                        dependency.appendNode("artifactId", it.name)
                        dependency.appendNode("version", it.version)
                        dependency.appendNode("scope", "compile")
                    }
                }
            }

            configuration.action?.execute(this)
        }

        project.createConfigurationAndAddArtifacts(OBFUSCATE_JAR_TASK, obfuscateJarTask)
    }

    private fun Project.createConfigurationAndAddArtifacts(shadowOriginalJarTask: TaskProvider<Jar>) {
        val shadowOriginalJarConfig = configurations.create(shadowOriginalJarTask.name)
        artifacts.add(shadowOriginalJarConfig.name, shadowOriginalJarTask)
    }

    private fun Project.createConfigurationAndAddArtifacts(configurationName: String, artifactTask: TaskProvider<Jar>) {
        val obfuscatedJarConfig = configurations.create(configurationName) {
            extendsFrom(configurations["runtimeClasspath"])
            isTransitive = configurations["runtimeClasspath"].isTransitive
            isCanBeResolved = configurations["runtimeClasspath"].isCanBeResolved
            isCanBeConsumed = configurations["runtimeClasspath"].isCanBeConsumed
        }

        artifacts.add(obfuscatedJarConfig.name, artifactTask)
    }
}

