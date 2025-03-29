package io.specmatic.gradle.jar.publishing

import com.vanniktech.maven.publish.MavenPublishBasePlugin
import io.specmatic.gradle.jar.massage.publishing
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

//internal fun Project.publishOriginalJar(
//    publicationConfigurations: MutableList<Action<MavenPublication>>, artifactId: String
//) {
//    pluginInfo("Configuring publication named '$group:$artifactId:$version' from component ${components["java"].name}")
//    publishing.publications.register("originalJar", MavenPublication::class.java) {
//        from(components["java"])
//        this.artifactId = artifactId
//        this.pom.packaging = "jar"
//
//        publicationConfigurations.forEach {
//            it.execute(this)
//        }
//    }
//
//    val jarTask = this.tasks.jar
//    this.configurations.create(jarTask.name)
//    pluginInfo("Adding output of ${jarTask.get().path} to artifact named ${jarTask.name}")
//    this.artifacts.add(jarTask.name, jarTask)
//}

internal fun Project.publishJar(
    publicationConfigurations: MutableList<Action<MavenPublication>>,
    artifactIdentifier: String,
    task: TaskProvider<out Jar>?,
    from: SoftwareComponent? = null
) {
    if (task == null && from == null) {
        throw GradleException("Either task or from must be provided!")
    }

    project.plugins.withType(MavenPublishBasePlugin::class.java) {
        val publicationName = artifactIdentifier
        publishing.publications.register(publicationName, MavenPublication::class.java) {
            if (from != null) {
                this.from(from)
            }
            if (task != null) {
                artifact(task) {
                    // but we remove the classifier when publishing, because we don't want the classifier in the published jar name.
                    classifier = null
                }
            }

            this.artifactId = artifactIdentifier
            this.pom.packaging = "jar"

            publicationConfigurations.forEach {
                it.execute(this)
            }

            pluginInfo("Configuring publication named $publicationName for artifact '$groupId:$artifactId:$version' using task ${task?.get()?.path ?: "none"} and component ${from?.name ?: "none"}")
        }

//    if (task.name != "jar") {
        if (task != null) {
            project.configurations.create(task.name)
            pluginInfo("Adding output of ${task.get().path} to artifact named ${task.name}")
            project.artifacts.add(task.name, task)
        }
//    }
    }
}
