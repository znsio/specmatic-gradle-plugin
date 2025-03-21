package io.specmatic.gradle.jar.publishing

import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.massage.shadow
import io.specmatic.gradle.jar.obfuscate.OBFUSCATE_JAR_TASK
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.shadow.SHADOW_OBFUSCATED_JAR
import io.specmatic.gradle.shadow.SHADOW_ORIGINAL_JAR
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get


internal fun Project.createObfuscatedOriginalJarPublicationTask(
    publishing: PublishingExtension, configuration: ProjectConfiguration
) {
    val obfuscateJarTask = this.tasks.named(OBFUSCATE_JAR_TASK, Jar::class.java)
    pluginInfo("Configuring publication named $OBFUSCATE_JAR_TASK on $this")
    publishing.publications.register(OBFUSCATE_JAR_TASK, MavenPublication::class.java) {
        artifact(obfuscateJarTask) {
            // we keep the classifier when building the jar, because we need to distinguish between the original and obfuscated jars, in the `lib` dir.
            // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
            classifier = null
        }
        artifactId = createArtifactIdFor(configuration, this)

        pom {
            packaging = "jar"
            withXml {
                val topLevel = asNode()
                val dependencies = topLevel.appendNode("dependencies")
                (configurations.getByName("compileClasspath").allDependencies - this@createObfuscatedOriginalJarPublicationTask.configurations.shadow.get().allDependencies).forEach {
                    val dependency = dependencies.appendNode("dependency")
                    dependency.appendNode("groupId", it.group)
                    dependency.appendNode("artifactId", it.name)
                    dependency.appendNode("version", it.version)
                    dependency.appendNode("scope", "compile")
                }
            }
        }

        createConfigurationAndAddArtifacts(
            OBFUSCATE_JAR_TASK, obfuscateJarTask
        )
        configuration.publicationConfigurations?.execute(this)
    }

}

internal fun Project.createShadowObfuscatedJarPublicationTask(
    publishing: PublishingExtension, configuration: ProjectConfiguration
) {
    val shadowObfuscatedJarTask = this.tasks.named(SHADOW_OBFUSCATED_JAR, Jar::class.java)
    publishing.publications.register(SHADOW_OBFUSCATED_JAR, MavenPublication::class.java) {
        this.artifact(shadowObfuscatedJarTask) {
            // we keep the classifier when building the jar, because we need to distinguish between the original and obfuscated jars, in the `lib` dir.
            // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
            this.classifier = null
        }
        this.artifactId = createArtifactIdFor(configuration, this)

        this.pom {
            this.packaging = "jar"
        }


        createConfigurationAndAddArtifacts(shadowObfuscatedJarTask)
        configuration.publicationConfigurations?.execute(this)
    }

}

internal fun Project.createShadowOriginalJarPublicationTask(
    publishing: PublishingExtension, configuration: ProjectConfiguration
) {
    val shadowOriginalJarTask = this.tasks.named(SHADOW_ORIGINAL_JAR, Jar::class.java)

    pluginInfo("Configuring publication named $SHADOW_ORIGINAL_JAR on $this")
    publishing.publications.register(SHADOW_ORIGINAL_JAR, MavenPublication::class.java) {
        artifact(shadowOriginalJarTask) {
            // we keep the classifier when building the jar, because we need to distinguish between the original and obfuscated jars, in the `lib` dir.
            // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
            classifier = null
        }
        artifactId = createArtifactIdFor(configuration, this)

        pom {
            this.packaging = "jar"
        }

        createConfigurationAndAddArtifacts(shadowOriginalJarTask)
        configuration.publicationConfigurations?.execute(this)
    }

}


internal fun Project.configureOriginalJarPublicationWhenObfuscationOrShadowPresent(
    publishing: PublishingExtension, configuration: ProjectConfiguration
) {
    pluginInfo("Configuring publication named $ORIGINAL_JAR on $this")
    publishing.publications.register(ORIGINAL_JAR, MavenPublication::class.java) {
        from(components["java"])
        artifactId = createArtifactIdFor(
            configuration, this
        )

        pom {
            packaging = "jar"
        }

        this@configureOriginalJarPublicationWhenObfuscationOrShadowPresent.createConfigurationAndAddArtifacts(
            ORIGINAL_JAR, this@configureOriginalJarPublicationWhenObfuscationOrShadowPresent.tasks.jar
        )
        configuration.publicationConfigurations?.execute(this)
    }
}


private fun Project.createArtifactIdFor(configuration: ProjectConfiguration, publication: MavenPublication): String {
    if (configuration.iAmABigFatLibrary) {
        when (publication.name) {
            ORIGINAL_JAR -> return "$name-dont-use-this-unless-you-know-what-you-are-doing"
            OBFUSCATE_JAR_TASK -> return name
            SHADOW_ORIGINAL_JAR -> return "$name-all-debug"
            SHADOW_OBFUSCATED_JAR -> return "$name-all-min"
        }
    } else when (publication.name) {
        ORIGINAL_JAR -> return "$name-dont-use-this-unless-you-know-what-you-are-doing"
        OBFUSCATE_JAR_TASK -> return "$name-min-with-transitive-deps"
        SHADOW_ORIGINAL_JAR -> return "$name-debug"
        SHADOW_OBFUSCATED_JAR -> return name
    }

    throw GradleException("Unable to determine artifactId for ${publication.name}")
}

private fun Project.createConfigurationAndAddArtifacts(shadowOriginalJarTask: TaskProvider<Jar>) {
    val shadowOriginalJarConfig = configurations.create(shadowOriginalJarTask.name)
    pluginInfo("Adding output of ${shadowOriginalJarTask.get().path} to artifact named ${shadowOriginalJarConfig.name}")
    artifacts.add(shadowOriginalJarConfig.name, shadowOriginalJarTask)
}

private fun Project.createConfigurationAndAddArtifacts(configurationName: String, artifactTask: TaskProvider<Jar>) {
    pluginInfo("Creating configuration $configurationName")
    val obfuscatedJarConfig = configurations.create(configurationName) {
        extendsFrom(configurations["runtimeClasspath"])
        isTransitive = configurations["runtimeClasspath"].isTransitive
        isCanBeResolved = configurations["runtimeClasspath"].isCanBeResolved
        isCanBeConsumed = configurations["runtimeClasspath"].isCanBeConsumed
    }

    artifacts.add(obfuscatedJarConfig.name, artifactTask)
}
