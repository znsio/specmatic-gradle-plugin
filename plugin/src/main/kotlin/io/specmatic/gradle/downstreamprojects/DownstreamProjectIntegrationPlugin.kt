package io.specmatic.gradle.downstreamprojects

import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.internal.GUtil
import java.io.File

class DownstreamProjectIntegrationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.afterEvaluate {
            if (specmaticExtension().downstreamDependentProjects.isNotEmpty()) {
                target.defineTasks()
            }
        }
    }
}

private fun Project.defineTasks() {
    val specmaticExtension = specmaticExtension()
    val validateDownstreamProjectTask = tasks.register("validateDownstreamProjects") {
        group = "verification"
        description = "Validate downstream project(s)"
    }

    val bumpVersionsInDownstreamProjectGradlePropertiesTask = tasks.register("bumpVersionsInDownstreamProjects") {
        group = "other"
        description = "Bump versions in downstream project(s)"
    }

    val cloneGithubWorkflowsRepo = cloneOrUpdateRepoTask("specmatic-github-workflows")

    specmaticExtension.downstreamDependentProjects.map { eachProject ->
        val cloneRepoIfNotExists = cloneOrUpdateRepoTask(eachProject)

        val validateTask = this@defineTasks.validateDownstreamProjectTask(eachProject, cloneRepoIfNotExists)
        validateTask.configure {
            dependsOn(cloneGithubWorkflowsRepo)
        }

        validateDownstreamProjectTask.configure {
            dependsOn(validateTask)
        }

        val bumpVersionTask = bumpSpecmaticVersionInProjectTask(
            eachProject,
            cloneRepoIfNotExists,
        )

        bumpVersionsInDownstreamProjectGradlePropertiesTask.configure {
            dependsOn(bumpVersionTask)
        }
    }
}

private fun Project.getDownstreamProjectDir(eachProject: String): File = file("../${eachProject}")

private val Project.specmaticModulePropertyKey: String
    get() = GUtil.toLowerCamelCase(project.name) + "Version"

private val Project.specmaticModuleVersion: Any?
    get() = project.findProperty(specmaticModulePropertyKey)

private fun Project.bumpSpecmaticVersionInProjectTask(
    eachProject: String,
    cloneRepoIfNotExists: TaskProvider<Task?>,
): TaskProvider<Exec?> = tasks.register("bumpVersion-$eachProject", Exec::class.java) {
    onlyIf("$specmaticModulePropertyKey is set") {
        project.hasProperty(specmaticModulePropertyKey)
    }
    dependsOn(cloneRepoIfNotExists)
    workingDir = getDownstreamProjectDir(eachProject)

    commandLine = listOf(
        "../specmatic-github-workflows/bin/fetch-artifacts",
        "-a",
        "${project.group}:${project.name}-min:${specmaticModuleVersion}",
        "-r",
        "lib"
    )

    doFirst {
        println("Bumping $specmaticModulePropertyKey in $eachProject")
        val gradlePropertiesFile = file("../${eachProject}/gradle.properties")
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

private fun Project.validateDownstreamProjectTask(
    eachRepo: String, cloneRepoIfNotExists: TaskProvider<Task?>
): TaskProvider<GradleBuild?> = tasks.register("validate-$eachRepo", GradleBuild::class.java) {
    dependsOn("publishAllPublicationsToStagingRepository")
    dependsOn("publishToMavenLocal")
    dependsOn(cloneRepoIfNotExists)

    dir = getDownstreamProjectDir(eachRepo)
    tasks = listOf("check")
    startParameter.projectProperties.put(specmaticModulePropertyKey, version.toString())
    doFirst {
        println("Validating $eachRepo using ./gradlew check -P$specmaticModulePropertyKey=$version")
    }
}


private fun Project.cloneOrUpdateRepoTask(
    eachRepo: String
): TaskProvider<Task?> = tasks.register("clone-$eachRepo") {
    doFirst {
        val downstreamProjectDir = getDownstreamProjectDir(eachRepo)
        if (!downstreamProjectDir.exists()) {
            project.providers.exec {
                commandLine("git", "clone", "https://github.com/znsio/$eachRepo.git", downstreamProjectDir)
            }
        } else {
            project.providers.exec {
                // pull the latest changes if the repo already exists
                commandLine("git", "pull", "--ff-only", "--no-rebase")
                workingDir = downstreamProjectDir
            }
        }
    }
}
