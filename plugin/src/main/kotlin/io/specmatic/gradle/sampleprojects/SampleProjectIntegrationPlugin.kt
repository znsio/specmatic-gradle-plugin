package io.specmatic.gradle.sampleprojects

import com.github.javaparser.utils.Utils
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.internal.GUtil
import java.io.File

class SampleProjectIntegrationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.afterEvaluate {
            target.defineTasks()
        }
    }
}

private fun Project.defineTasks() {
    val specmaticExtension = specmaticExtension()
    val validateSampleProjectTask = tasks.register("validateSampleProjects")
    val bumpVersionsInSampleProjectGradlePropertiesTask = tasks.register("bumpVersionsInSampleProjects")

    val cloneGithubWorkflowsRepo = cloneOrUpdateRepoTask("specmatic-github-workflows")

    specmaticExtension.sampleProjects.map { eachSampleProject ->
        val cloneSampleRepoIfNotExists = cloneOrUpdateRepoTask(eachSampleProject)

        val validateTask = validateSampleProjectTask(eachSampleProject, cloneSampleRepoIfNotExists)
        validateTask.configure {
            dependsOn(cloneGithubWorkflowsRepo)
        }

        validateSampleProjectTask.configure {
            dependsOn(validateTask)
        }

        val bumpVersionTask = bumpSpecmaticVersionInSampleProjectTask(
            eachSampleProject,
            cloneSampleRepoIfNotExists,
        )

        bumpVersionsInSampleProjectGradlePropertiesTask.configure {
            dependsOn(bumpVersionTask)
        }
    }
}

private fun Project.getSampleProjectDir(eachSampleProject: String): File = file("../${eachSampleProject}")

private val Project.specmaticModulePropertyKey: String
    get() = GUtil.toLowerCamelCase(project.name) + "Version"

private val Project.specmaticModuleVersion: Any?
    get() = project.findProperty(specmaticModulePropertyKey)

private fun Project.bumpSpecmaticVersionInSampleProjectTask(
    eachSampleProject: String,
    cloneSampleRepoIfNotExists: TaskProvider<Task?>,
): TaskProvider<Exec?> = tasks.register("bumpVersion-$eachSampleProject", Exec::class.java) {
    onlyIf("$specmaticModulePropertyKey is set") {
        project.hasProperty(specmaticModulePropertyKey)
    }
    dependsOn(cloneSampleRepoIfNotExists)
    val sampleProjectDir = getSampleProjectDir(eachSampleProject)
    workingDir = sampleProjectDir

    commandLine = listOf(
        "../specmatic-github-workflows/bin/fetch-artifacts",
        "-a",
        "${project.group}:${project.name}-min:${specmaticModuleVersion}",
        "-r",
        "lib"
    )

    doFirst {
        println("Bumping $specmaticModulePropertyKey in $eachSampleProject")
        val gradlePropertiesFile = file("../${eachSampleProject}/gradle.properties")
        val content = gradlePropertiesFile.readLines().joinToString("\n") { line ->
            if (line.trim().startsWith("#")) {
                line
            } else {
                line.replace(
                    Regex("""\s*${specmaticModulePropertyKey}\s*=\s*.*"""),
                    "${specmaticModulePropertyKey}=${specmaticModuleVersion}"
                )
            }
        }
        gradlePropertiesFile.writeText(content)
    }
}

private fun Project.validateSampleProjectTask(
    eachSampleProject: String, cloneSampleRepoIfNotExists: TaskProvider<Task?>
): TaskProvider<GradleBuild?> = tasks.register("validate-$eachSampleProject", GradleBuild::class.java) {
    dependsOn("publishAllPublicationsToStagingRepository")
    dependsOn("publishToMavenLocal")
    dependsOn(cloneSampleRepoIfNotExists)

    val sampleProjectDir = getSampleProjectDir(eachSampleProject)
    dir = sampleProjectDir
    tasks = listOf("check")
    startParameter.projectProperties.put(specmaticModulePropertyKey, version.toString())
    doFirst {
        println("Validating $eachSampleProject using ./gradlew check -P$specmaticModulePropertyKey=$version")
    }
}


private fun Project.cloneOrUpdateRepoTask(
    eachSampleProject: String
): TaskProvider<Task?> = tasks.register("clone-$eachSampleProject") {
    doFirst {
        val sampleProjectDir = getSampleProjectDir(eachSampleProject)
        if (!sampleProjectDir.exists()) {
            project.providers.exec {
                commandLine("git", "clone", "https://github.com/znsio/$eachSampleProject.git", sampleProjectDir)
            }
        } else {
            project.providers.exec {
                // pull the latest changes if the repo already exists
                commandLine("git", "pull", "--ff-only", "--no-rebase")
                workingDir = sampleProjectDir
            }
        }
    }
}
