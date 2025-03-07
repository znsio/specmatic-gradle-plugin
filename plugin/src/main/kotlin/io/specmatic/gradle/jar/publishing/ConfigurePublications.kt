package io.specmatic.gradle.jar.publishing

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishPlugin
import com.vanniktech.maven.publish.SonatypeHost
import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.extensions.PublicationType
import io.specmatic.gradle.findSpecmaticExtension
import io.specmatic.gradle.obfuscate.OBFUSCATE_JAR_TASK
import io.specmatic.gradle.pluginDebug
import io.specmatic.gradle.shadow.SHADOW_OBFUSCATED_JAR
import io.specmatic.gradle.shadow.SHADOW_ORIGINAL_JAR
import io.specmatic.gradle.shadow.jarTaskProvider
import org.gradle.api.Action
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
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

class ConfigurePublications(project: Project, projectConfiguration: ProjectConfiguration) {
    init {
        configure(project, projectConfiguration)
    }

    private fun configure(project: Project, projectConfiguration: ProjectConfiguration) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)

        project.pluginManager.withPlugin("signing") {
            project.extensions.getByType(SigningExtension::class.java).apply {
                val keyId = System.getenv("SPECMATIC_GPG_KEY_ID")
                val key = System.getenv("SPECMATIC_GPG_PRIVATE_KEY")
                val password = System.getenv("SPECMATIC_GPG_PRIVATE_KEY_PASSPHRASE")
                useInMemoryPgpKeys(keyId, key, password)
            }

            project.tasks.withType(Sign::class.java).configureEach {
                isRequired = System.getenv("SPECMATIC_GPG_KEY_ID") != null
            }
        }

        project.pluginManager.withPlugin("com.vanniktech.maven.publish") {

            project.tasks.withType(GenerateModuleMetadata::class.java).configureEach {
                enabled = false
            }

            pluginDebug("Configuring maven publishing on ${project}")
            val stagingRepo = project.rootProject.layout.buildDirectory.dir("mvn-repo")

            val publishing = project.extensions.getByType(PublishingExtension::class.java)

            publishing.publications.whenObjectAdded({
                if (this is MavenPublication) {
                    projectConfiguration.publicationConfigurations?.execute(this)
                }
            })
            publishing.publications.all {
                if (this is MavenPublication) {
                    projectConfiguration.publicationConfigurations?.execute(this)
                }
            }

            configurePublishingToStagingRepo(publishing, project, stagingRepo)

            configureJarPublishing(publishing, project, projectConfiguration)
            configureMavenCentralPublishing(project)
        }
    }

    private fun configureMavenCentralPublishing(project: Project) {
        project.extensions.getByType(MavenPublishBaseExtension::class.java).apply {
            val specmaticExtension = findSpecmaticExtension(project.rootProject)
                ?: throw GradleException("SpecmaticGradleExtension not found")
            if (specmaticExtension.publishToMavenCentral) {
                publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, false)
            } else {
                pluginDebug("publishToMavenCentral is not set. Not publishing to Maven Central")
            }
            signAllPublications()
        }
    }

    private fun configureJarPublishing(
        publishing: PublishingExtension, project: Project, configuration: ProjectConfiguration
    ) {
        if (configuration.publicationTypes.isNotEmpty()) {
            configureOriginalJarPublicationWhenObfuscationOrShadowPresent(
                publishing, project, configuration.publicationConfigurations
            )

            removeTasksAndPublicationsWeDoNotWant(publishing, project)

        }

        if (configuration.publicationTypes.contains(PublicationType.OBFUSCATED_ORIGINAL)) {
            configureObfuscatedOriginalJarPublication(publishing, project, configuration.publicationConfigurations)
        }

        if (configuration.publicationTypes.contains(PublicationType.SHADOWED_ORIGINAL)) {
            configureShadowOriginalJarPublication(publishing, project, configuration.publicationConfigurations)
        }

        if (configuration.publicationTypes.contains(PublicationType.SHADOWED_OBFUSCATED)) {
            configureShadowObfuscatedJarPublication(publishing, project, configuration.publicationConfigurations)
        }
    }

    private fun removeTasksAndPublicationsWeDoNotWant(publishing: PublishingExtension, project: Project) {
        // this is added by vanniktech's publish plugin, we don't want it

        publishing.publications.removeIf {
            pluginDebug("Removing publication ${it.name}")
            it.name == "maven"
        }
        project.tasks.named("publishMavenPublicationToStagingRepository").get().enabled = false
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
        publishing: PublishingExtension, project: Project, configuration: Action<MavenPublication>?
    ) {
        val shadowObfuscatedJarTask = project.tasks.named(SHADOW_OBFUSCATED_JAR, Jar::class.java)
        publishing.publications.register(SHADOW_OBFUSCATED_JAR, MavenPublication::class.java) {
            artifact(shadowObfuscatedJarTask)
            artifactId = project.name + "-shadow-obfuscated"
            pom.packaging = "jar"

            configuration?.execute(this)
        }

        project.createConfigurationAndAddArtifacts(shadowObfuscatedJarTask)
    }

    private fun configureShadowOriginalJarPublication(
        publishing: PublishingExtension, project: Project, configuration: Action<MavenPublication>?
    ) {
        val shadowOriginalJarTask = project.tasks.named(SHADOW_ORIGINAL_JAR, Jar::class.java)

        publishing.publications.register(SHADOW_ORIGINAL_JAR, MavenPublication::class.java) {
            artifact(shadowOriginalJarTask)
            artifactId = project.name + "-shadow-original"
            pom.packaging = "jar"
            configuration?.execute(this)
        }

        project.createConfigurationAndAddArtifacts(shadowOriginalJarTask)
    }


    private fun configureOriginalJarPublicationWhenObfuscationOrShadowPresent(
        publishing: PublishingExtension, project: Project, configuration: Action<MavenPublication>?
    ) {
        publishing.publications.register("originalJar", MavenPublication::class.java) {
            from(project.components["java"])
            artifactId = project.name + "-original"
            pom.packaging = "jar"
            configuration?.execute(this)
        }

        project.createConfigurationAndAddArtifacts("originalJar", project.jarTaskProvider())
    }

    private fun configureObfuscatedOriginalJarPublication(
        publishing: PublishingExtension, project: Project, configuration: Action<MavenPublication>?
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

            configuration?.execute(this)
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