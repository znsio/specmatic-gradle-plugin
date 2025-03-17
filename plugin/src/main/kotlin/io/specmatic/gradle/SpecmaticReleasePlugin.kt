package io.specmatic.gradle

import io.specmatic.gradle.license.pluginInfo
import net.researchgate.release.ReleasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class SpecmaticReleasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(ReleasePlugin::class.java)
        target.configureGithubRelease()
    }
}

private fun Project.configureGithubRelease() {
    plugins.withType(ReleasePlugin::class.java) {
        project.afterEvaluate {

            project.specmaticExtension().projectConfigurations.forEach { eachProject, eachProjectConfig ->
                if (eachProjectConfig.githubReleaseEnabled) {
                    val createGithubReleaseTask = findOrCreateGithubReleaseTask()

                    createGithubReleaseTask.configure {
                        publish(eachProject, eachProjectConfig.githubRelease)
                    }

                    project.tasks.named("preTagCommit") {
                        finalizedBy(createGithubReleaseTask)
                    }
                }
            }
        }

    }
}

private fun Project.findOrCreateGithubReleaseTask(): TaskProvider<CreateGithubReleaseTask> {
    val existingTask = tasks.findByName("createGithubRelease")
    return if (existingTask == null) {
        pluginInfo("Creating createGithubRelease task")
        tasks.register("createGithubRelease", CreateGithubReleaseTask::class.java) {
            group = "release"
            description = "Create a Github release"
        }
    } else {
        tasks.named("createGithubRelease", CreateGithubReleaseTask::class.java)
    }
}
