package io.specmatic.gradle

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import io.specmatic.gradle.extensions.PublicationDefinition
import io.specmatic.gradle.extensions.PublicationType
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get

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
        project.plugins.apply(com.vanniktech.maven.publish.MavenPublishPlugin::class.java)

        project.pluginManager.withPlugin("com.vanniktech.maven.publish") {
            println("Configuring maven publishing on ${project}")
            val stagingRepo = project.layout.buildDirectory.dir("mvn-repo")
            val publications = mutableListOf<MavenPublication>()

            val specmaticExtension =
                findSpecmaticExtension(project) ?: throw GradleException("SpecmaticGradleExtension not found")

            val publishing = project.extensions.getByType(PublishingExtension::class.java)

            publishing.publications.whenObjectAdded({
                if (this is MavenPublication) {
                    configuration.action?.execute(this)
                }
            })

            if (configuration.types.isNotEmpty()) {
                if (!specmaticExtension.obfuscatedProjects.containsKey(project) && !specmaticExtension.shadowConfigurations.containsKey(
                        project
                    )
                ) {
                    val message =
                        "ERROR. $project is both obfuscated and shadowed. Don't specify publication types in `specmatic` block for $project"
                    println()
                    println("!".repeat(message.length))
                    println(message)
                    println("ERROR. The build will fail".padEnd(message.length))
                    println("!".repeat(message.length))
                    println()
                }
            }

            if (configuration.types.contains(PublicationType.SHADOWED_ORIGINAL)) {
                publications.add(configureShadowOriginalJarPublication(publishing, project, configuration))
            }

            if (configuration.types.contains(PublicationType.OBFUSCATED_ORIGINAL)) {
                publications.add(configureObfuscatedOriginalJarPublication(publishing, project, configuration))
            }

            if (configuration.types.contains(PublicationType.SHADOWED_OBFUSCATED)) {
                publications.add(configureShadowObfuscatedJarPublication(publishing, project, configuration))
            }

            publishing.repositories {
                maven {
                    url = project.uri(stagingRepo)
                }
            }



            project.extensions.getByType(MavenPublishBaseExtension::class.java).apply {
                publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, false)
                signAllPublications()
            }
        }

    }

    private fun configureShadowObfuscatedJarPublication(
        publishing: PublishingExtension, project: Project, configuration: PublicationDefinition
    ): MavenPublication {
        return publishing.publications.register("shadow-obfuscated", MavenPublication::class.java) {
            artifact(project.tasks.named("shadowObfuscatedJar"))
            artifactId = project.name + "-shadow-obfuscated"
            pom.packaging = "jar"

            configuration.action?.execute(this)
        }.get()
    }

    private fun configureShadowOriginalJarPublication(
        publishing: PublishingExtension, project: Project, configuration: PublicationDefinition
    ): MavenPublication {
        return publishing.publications.register("shadow-original", MavenPublication::class.java) {
            artifact(project.tasks.named("shadowOriginalJar"))
            artifactId = project.name + "-shadow-original"
            pom.packaging = "jar"

            configuration.action?.execute(this)
        }.get()
    }

    private fun configureObfuscatedOriginalJarPublication(
        publishing: PublishingExtension, project: Project, configuration: PublicationDefinition
    ): MavenPublication {
        return publishing.publications.register("obfuscated-original", MavenPublication::class.java) {
            // use the same dependency tree as original
            from(project.components["java"])

            println("Adding artifact from obfuscateJar")
            // use a different artifact
            val obfuscateJarTask = project.tasks.named(OBFUSCATE_JAR_TASK)
            artifact(obfuscateJarTask)
            artifactId = project.name + "-obfuscated-original"
            pom.packaging = "jar"

            configuration.action?.execute(this)
        }.get()
    }

}
