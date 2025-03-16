package io.specmatic.gradle.jar.publishing

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishBasePlugin
import com.vanniktech.maven.publish.SonatypeHost
import io.specmatic.gradle.extensions.MavenCentral
import io.specmatic.gradle.extensions.MavenInternal
import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.extensions.PublicationType
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

const val ORIGINAL_JAR = "originalJar"


class ConfigurePublicationsPlugin() : Plugin<Project> {

    override fun apply(target: Project) {
        target.afterEvaluate {
            val specmaticExtension = target.specmaticExtension()
            val projectConfiguration = specmaticExtension.projectConfigurations[target]
            if (projectConfiguration?.publicationEnabled == true) {
                configure(target, projectConfiguration)
            }
        }
    }

    private fun configure(project: Project, projectConfiguration: ProjectConfiguration) {
        project.plugins.apply(MavenPublishBasePlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)

        configureSigning(project)
        configuerPublishing(project, projectConfiguration)
    }

    private fun configureSigning(project: Project) {
        project.plugins.withType(SigningPlugin::class.java) {
            project.extensions.getByType(SigningExtension::class.java).apply {
                val keyId = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId")
                val key = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
                val password = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
                useInMemoryPgpKeys(keyId, key, password)
            }

            project.tasks.withType(Sign::class.java).configureEach {
                isRequired = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId") != null
            }
        }
    }

    private fun configuerPublishing(project: Project, projectConfiguration: ProjectConfiguration) {
        project.plugins.withType(MavenPublishBasePlugin::class.java) {
            project.pluginInfo("Configuring maven publishing on $project")
            val stagingRepo = project.rootProject.layout.buildDirectory.dir("mvn-repo")
            val publishing = project.extensions.getByType(PublishingExtension::class.java)

            publishing.publications.withType(MavenPublication::class.java) {
                projectConfiguration.publicationConfigurations?.execute(this)
            }

            publishing.configurePublishingToStagingRepo(project, stagingRepo)

            configureJarPublishing(publishing, project, projectConfiguration)
            configureMavenCentralPublishing(publishing, project)
        }
    }

    private fun configureMavenCentralPublishing(publishing: PublishingExtension, project: Project) {
        project.extensions.getByType(MavenPublishBaseExtension::class.java).apply {
            val specmaticExtension = project.rootProject.specmaticExtension()
            specmaticExtension.publishTo.forEach { publishTarget ->
                if (publishTarget is MavenCentral) {
                    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, false)
                } else if (publishTarget is MavenInternal) {
                    val repo = publishTarget
                    project.pluginInfo("Configuring publishing to ${repo.repoName}")
                    publishing.repositories.maven {
                        name = repo.repoName
                        url = repo.url
                        credentials(PasswordCredentials::class.java)
                    }
                } else {
                    project.pluginInfo("publishToMavenCentral is not set. Not publishing to Maven Central")
                }
            }

            signAllPublications()
        }
    }

    private fun configureJarPublishing(
        publishing: PublishingExtension, project: Project, configuration: ProjectConfiguration
    ) {
        if (configuration.publicationTypes.isNotEmpty()) {
            project.configureOriginalJarPublicationWhenObfuscationOrShadowPresent(
                publishing, configuration
            )

        }

        if (configuration.publicationTypes.contains(PublicationType.OBFUSCATED_ORIGINAL)) {
            project.createObfuscatedOriginalJarPublicationTask(publishing, configuration)
        }

        if (configuration.publicationTypes.contains(PublicationType.SHADOWED_ORIGINAL)) {
            project.createShadowOriginalJarPublicationTask(publishing, configuration)
        }

        if (configuration.publicationTypes.contains(PublicationType.SHADOWED_OBFUSCATED)) {
            project.createShadowObfuscatedJarPublicationTask(publishing, configuration)
        }
    }
}

private fun PublishingExtension.configurePublishingToStagingRepo(project: Project, stagingRepo: Provider<Directory>) {
    repositories.maven {
        name = "staging"
        url = project.uri(stagingRepo)
    }
}
