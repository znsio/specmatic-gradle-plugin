package io.specmatic.gradle.jar.publishing

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.massage.publishing
import io.specmatic.gradle.jar.massage.unobfuscatedShadowJarTask
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.get


internal fun Project.publishOriginalJar(
    publicationConfigurations: MutableList<Action<MavenPublication>>, artifactId: String
) {
    pluginInfo("Configuring publication named ${this.name} with artifactID $artifactId")
    publishing.publications.register("originalJar", MavenPublication::class.java) {
        from(components["java"])
        this.artifactId = artifactId
        this.pom.packaging = "jar"

        publicationConfigurations.forEach {
            it.execute(this)
        }
    }

    val jarTask = this.tasks.jar
    this.configurations.create(jarTask.name)
    pluginInfo("Adding output of ${jarTask.get().path} to artifact named ${jarTask.name}")
    this.artifacts.add(jarTask.name, jarTask)
}

internal fun Project.publishUnobfuscatedShadowJar(
    unobfuscatedShadowJarTask: TaskProvider<ShadowJar>,
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    artifactId: String
) {
    pluginInfo("Configuring publication named ${this.name} with artifactID $artifactId")
    publishing.publications.register(UNOBFUSCATED_SHADOW_JAR, MavenPublication::class.java) {
        artifact(unobfuscatedShadowJarTask) {
            // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
            classifier = null
        }
        this.artifactId = artifactId
        this.pom.packaging = "jar"

        publicationConfigurations.forEach {
            it.execute(this)
        }
    }

    val jarTask = this.tasks.unobfuscatedShadowJarTask
    this.configurations.create(jarTask.name)
    pluginInfo("Adding output of ${jarTask.get().path} to artifact named ${jarTask.name}")
    this.artifacts.add(jarTask.name, jarTask)
}

internal fun Project.publishObfuscatedOriginalJar(
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    artifactId: String
) {
    TODO()
}

internal fun Project.publishObfuscatedShadowJar(
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    artifactId: String
) {
    TODO()
}
