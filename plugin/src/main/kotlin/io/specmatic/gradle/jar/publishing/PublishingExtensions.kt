package io.specmatic.gradle.jar.publishing

import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.massage.obfuscateJarTask
import io.specmatic.gradle.jar.massage.publishing
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
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

internal fun Project.publishJar(
    task: TaskProvider<out Jar>,
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    artifactId: String
) {
    pluginInfo("Configuring publication named ${this.name} with artifactID $artifactId")
    publishing.publications.register(task.name, MavenPublication::class.java) {
        artifact(task) {
            // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
            classifier = null
        }
        this.artifactId = artifactId
        this.pom.packaging = "jar"

        publicationConfigurations.forEach {
            it.execute(this)
        }
    }

    this.configurations.create(task.name)
    pluginInfo("Adding output of ${task.get().path} to artifact named ${task.name}")
    this.artifacts.add(task.name, task)
}

