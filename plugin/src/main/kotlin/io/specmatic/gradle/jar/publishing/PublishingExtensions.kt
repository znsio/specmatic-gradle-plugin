package io.specmatic.gradle.jar.publishing

import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.massage.publishing
import io.specmatic.gradle.jar.massage.shadow
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get

internal fun Project.createUnobfuscatedJarPublication(
    publicationConfigurations: MutableList<Action<MavenPublication>>, artifactIdentifier: String
) {
    val jarTask = tasks.jar
    val publication = publishJar(
        publicationConfigurations, JavaComponentPublisher(artifactIdentifier, components["java"])
    )
    createConfigurationAndAddArtifacts(publication.name, jarTask)
}

internal fun Project.createObfuscatedOriginalJarPublication(
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    task: TaskProvider<out Jar>,
    artifactIdentifier: String
) {
    val publication = publishJar(publicationConfigurations, object : PublishingConfigurer {
        override fun configure(publication: MavenPublication) {
            pluginInfo("Configuring publication named ${name()} for artifact '${publication.groupId}:${publication.artifactId}:${publication.version}' using task ${task.get().path}")
            publication.artifact(task) {
                // we keep the classifier when building the jar, because we need to distinguish between the original and obfuscated jars, in the `lib` dir.
                // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
                classifier = null
            }
            publication.artifactId = artifactIdentifier
            publication.pom {
                withXml {
                    val topLevel = asNode()
                    val dependencies = topLevel.appendNode("dependencies")
                    (configurations["compileClasspath"].allDependencies - configurations.shadow.get().allDependencies).forEach {
                        val dependency = dependencies.appendNode("dependency")
                        dependency.appendNode("groupId", it.group)
                        dependency.appendNode("artifactId", it.name)
                        dependency.appendNode("version", it.version)
                        dependency.appendNode("scope", "compile")
                    }
                }
            }
        }

        override fun name(): String = artifactIdentifier
    })
    createConfigurationAndAddArtifacts(publication.name, task)
}

internal fun Project.createShadowedObfuscatedJarPublication(
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    task: TaskProvider<out Jar>,
    artifactIdentifier: String
) {
    publishJar(
        publicationConfigurations,
        ArtifactPublishingConfigurer(
            artifactIdentifier, task
        )
    )

    createConfigurationAndAddArtifacts(task)
}


internal fun Project.createShadowedUnobfuscatedJarPublication(
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    task: TaskProvider<out Jar>,
    artifactIdentifier: String
) {
    publishJar(publicationConfigurations, ArtifactPublishingConfigurer(artifactIdentifier, task))
    createConfigurationAndAddArtifacts(task)
}

private fun Project.createConfigurationAndAddArtifacts(configurationName: String, artifactTask: TaskProvider<out Jar>) {
    pluginInfo("Creating configuration $configurationName")
    val configuration = configurations.create(configurationName) {
        extendsFrom(configurations["runtimeClasspath"])
        isTransitive = configurations["runtimeClasspath"].isTransitive
        isCanBeResolved = configurations["runtimeClasspath"].isCanBeResolved
        isCanBeConsumed = configurations["runtimeClasspath"].isCanBeConsumed
    }
    pluginInfo("Adding output of ${artifactTask.get().path} to artifact named ${configuration.name}")
    artifacts.add(configuration.name, artifactTask)
}


interface PublishingConfigurer {
    fun configure(publication: MavenPublication)
    fun name(): String
}

class JavaComponentPublisher(private val artifactIdentifier: String, private val component: SoftwareComponent) :
    PublishingConfigurer {
    override fun configure(publication: MavenPublication) {
        publication.from(component)
        publication.artifactId = artifactIdentifier
        pluginInfo("Configuring publication named ${name()} for artifact '${publication.groupId}:${publication.artifactId}:${publication.version}' using component ${component.name}")
    }

    override fun name(): String = artifactIdentifier

}

class ArtifactPublishingConfigurer(private val artifactIdentifier: String, private val task: TaskProvider<out Jar>) :
    PublishingConfigurer {
    override fun configure(publication: MavenPublication) {
        pluginInfo("Configuring publication named ${name()} for artifact '${publication.groupId}:${publication.artifactId}:${publication.version}' using task ${task.get().path}")
        publication.artifact(task) {
            // we keep the classifier when building the jar, because we need to distinguish between the original and obfuscated jars, in the `lib` dir.
            // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
            classifier = null
        }
        publication.artifactId = artifactIdentifier
    }

    override fun name(): String = artifactIdentifier
}

private fun Project.publishJar(
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    configurer: PublishingConfigurer,
): NamedDomainObjectProvider<MavenPublication> {
    return publishing.publications.register(configurer.name(), MavenPublication::class.java) {
        pom.packaging = "jar"

        configurer.configure(this)

        publicationConfigurations.forEach {
            it.execute(this)
        }
    }
}

private fun Project.createConfigurationAndAddArtifacts(taskProvider: TaskProvider<out Jar>) {
    val shadowOriginalJarConfig = configurations.create(taskProvider.name)
    pluginInfo("Adding output of ${taskProvider.get().path} to artifact named ${shadowOriginalJarConfig.name}")
    artifacts.add(shadowOriginalJarConfig.name, taskProvider)
}
