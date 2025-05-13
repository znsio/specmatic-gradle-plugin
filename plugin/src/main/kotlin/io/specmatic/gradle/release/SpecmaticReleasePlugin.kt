package io.specmatic.gradle.release

import io.specmatic.gradle.CreateGithubReleaseTask
import io.specmatic.gradle.features.BaseDistribution
import io.specmatic.gradle.features.GithubReleaseFeature
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.license.pluginWarn
import io.specmatic.gradle.specmaticExtension
import io.specmatic.gradle.versioninfo.versionInfo
import net.researchgate.release.BuildEventsListenerRegistryProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskOperationDescriptor

class SpecmaticReleasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(BasePlugin::class.java)
        target.afterEvaluate {
            target.configureReleaseTasks()
        }
    }
}

private fun Project.configureReleaseTasks() {
    val originalGitCommit = project.versionInfo().gitCommit

    val preReleaseCheckTask = getPreReleaseCheckTask()
    val removeSnapshotTask = preReleaseBumpTask(preReleaseCheckTask)
    val preReleaseValidationTask = preReleaseValidationTask(removeSnapshotTask)
    val runReleaseLifecycleHooksTask = runReleaseLifecycleHooksTask(preReleaseValidationTask)
    val createReleaseTagTask = runReleaseTagTask(runReleaseLifecycleHooksTask)
    val postReleaseBumpTask = postReleaseBumpTask(createReleaseTagTask)
    val gitPushTask = gitPushTask(postReleaseBumpTask)
    val createGithubReleaseTask = findOrCreateGithubReleaseTask(gitPushTask)

    val releaseTask = project.tasks.register("release") {
        group = "release"
        description = "Release the project"
        dependsOn(createGithubReleaseTask)
    }

    objects.newInstance(BuildEventsListenerRegistryProvider::class.java).buildEventsListenerRegistry.onTaskCompletion(
        project.provider {
            OperationCompletionListener { finishEvent ->
                val descriptor = finishEvent.descriptor
                val operationResult = finishEvent.result

                if (operationResult is TaskFailureResult && descriptor is TaskOperationDescriptor) {
                    if (gradle.taskGraph.hasTask(releaseTask.get().path)) {
                        project.pluginWarn("Release failed, reverting changes")
                        revertGitChanges(originalGitCommit)
                    }
                }
            }
        })
}


private fun Project.gitPushTask(runReleaseLifecycleHooksTask: TaskProvider<*>): TaskProvider<*> =
    project.tasks.register("releaseGitPush", GitPushTask::class.java) {
        dependsOn(runReleaseLifecycleHooksTask)
        group = "release"
        description = "Perform a git push after release"

        rootDir.set(project.rootProject.rootDir)
    }

private fun Project.postReleaseBumpTask(dependentTask: TaskProvider<*>): TaskProvider<*> =
    project.tasks.register("postReleaseBump", PostReleaseBump::class.java) {
        dependsOn(dependentTask)
        group = "release"
        description = "Post-release bump"

        rootDir.set(project.rootProject.rootDir)
        postReleaseVersion.set(project.provider { project.property("release.newVersion").toString() })
    }


fun Project.preReleaseValidationTask(removeSnapshotTask: TaskProvider<*>): TaskProvider<*> {
    return if (project.hasProperty("functionalTestingHack") && project.property("functionalTestingHack") == "true") {
        project.tasks.register("preReleaseValidationHooks") {
            dependsOn(removeSnapshotTask)
            group = "release"
            description = "Run pre-release validation tasks (add your tasks here) - test hax"
        }
    } else {
        project.tasks.register("preReleaseValidationHooks", GradleBuild::class.java) {
            dependsOn(removeSnapshotTask)
            group = "release"
            description = "Run pre-release validation tasks (add your tasks here)"

            startParameter = project.gradle.startParameter.newInstance()
            startParameter.projectProperties.putAll(project.gradle.startParameter.projectProperties)
            buildName = "(pre-release-validation)"

            tasks = listOf("clean", *project.specmaticExtension().preReleaseVadlidateTasks.toTypedArray())
        }
    }
}

private fun Project.runReleaseLifecycleHooksTask(
    removeSnapshotTask: TaskProvider<*>,
): TaskProvider<*> {
    return if (project.hasProperty("functionalTestingHack") && project.property("functionalTestingHack") == "true") {
        project.tasks.register("runReleaseLifecycleHooks") {
            dependsOn(removeSnapshotTask)
            group = "release"
            description = "Run release lifecycle hooks (add your tasks here) - test hax"
        }
    } else {
        project.tasks.register("runReleaseLifecycleHooks", GradleBuild::class.java) {
            dependsOn(removeSnapshotTask)
            group = "release"
            description = "Run release lifecycle hooks (add your tasks here)"

            startParameter = project.gradle.startParameter.newInstance()
            startParameter.projectProperties.putAll(project.gradle.startParameter.projectProperties)
            buildName = "(release-publish)"

            tasks = listOf("clean", *project.specmaticExtension().releasePublishTasks.toTypedArray())
        }
    }
}


private fun Project.runReleaseTagTask(dependentTask: TaskProvider<*>): TaskProvider<*> =
    project.tasks.register("createReleaseTag", CreateReleaseTagTask::class.java) {
        dependsOn(dependentTask)
        group = "release"
        description = "Create release tag"

        rootDir.set(project.rootProject.rootDir)
        releaseVersion.set(project.provider { project.property("release.releaseVersion").toString() })
    }


private fun Project.preReleaseBumpTask(dependentTask: TaskProvider<*>): TaskProvider<*> =
    project.tasks.register("preReleaseBump", PreReleaseVersionBump::class.java) {
        dependsOn(dependentTask)
        group = "release"
        description = "Bump version before release"

        rootDir.set(project.rootProject.rootDir)
        releaseVersion.set(project.provider { project.property("release.releaseVersion").toString() })
    }

private fun Project.getPreReleaseCheckTask(): TaskProvider<*> =
    project.tasks.register("preReleaseCheck", PreReleaseCheck::class.java) {
        group = "release"
        description = "Pre-release checks"

        rootDir.set(project.rootProject.rootDir)
    }

private fun Project.revertGitChanges(originalGitCommit: String) {
    GitOperations(rootProject.rootDir, project.properties, logger).apply {
        pluginInfo("Reverting changes to original commit $originalGitCommit")
        resetHardTo(originalGitCommit)
    }
}

private fun Project.findOrCreateGithubReleaseTask(dependentTasks: TaskProvider<*>): TaskProvider<*> {
    val existingTask = tasks.findByName("createGithubRelease")
    return if (existingTask == null) {
        pluginInfo("Creating createGithubRelease task")
        tasks.register("createGithubRelease", CreateGithubReleaseTask::class.java) {
            dependsOn(dependentTasks)
            group = "release"
            description = "Create a Github release"

            releaseVersion.set(project.provider { project.property("release.releaseVersion").toString() })

            specmaticExtension().projectConfigurations.forEach { (eachProject, eachProjectConfig) ->
                if (eachProjectConfig is GithubReleaseFeature) {
                    publish(eachProject, (eachProjectConfig as BaseDistribution).githubRelease)
                }
            }

        }
    } else {
        tasks.named("createGithubRelease", CreateGithubReleaseTask::class.java)
    }
}
