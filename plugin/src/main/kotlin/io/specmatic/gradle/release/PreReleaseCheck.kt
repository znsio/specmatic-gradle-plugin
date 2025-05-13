package io.specmatic.gradle.release

import io.specmatic.gradle.license.pluginWarn
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault
abstract class PreReleaseCheck : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @TaskAction
    fun release() {
        assertNoSnapshotDependencies()

        GitOperations(rootDir.get(), project.properties, logger).apply {
            assertMainBranch()
            assertRepoNotDirty()
            assertNoIncomingOrOutgoingChanges()
        }

    }

    private fun assertNoSnapshotDependencies() {
        if (project.hasProperty("allowSnapshotDependencies") && project.property("allowSnapshotDependencies") == "true") {
            project.pluginWarn("Skipping snapshot dependency check as per user request.")
            return
        }
        project.allprojects.forEach { project ->
            val matchingDependencies = (project.configurations + project.buildscript.configurations).flatMap { cfg ->
                cfg.dependencies.matching { dep ->
                    dep.version?.contains("SNAPSHOT") == true
                }
            }

            if (matchingDependencies.isNotEmpty()) {
                throw GradleException(
                    "There are dependencies with SNAPSHOT versions:\n" + matchingDependencies.joinToString(
                        separator = "\n"
                    ) { "- ${it.group}:${it.name}:${it.version}" } + "\nPlease remove them before creating a release. Run with `-PallowSnapshotDependencies=true` to disable.")
            } else {
                project.pluginWarn("No SNAPSHOT dependencies found.")
            }
        }
    }


}
