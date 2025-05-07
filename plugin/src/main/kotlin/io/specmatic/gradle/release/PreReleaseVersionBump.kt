package io.specmatic.gradle.release

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault
abstract class PreReleaseVersionBump : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val releaseVersion: Property<String>

    @TaskAction
    fun preReleaseGitCommit() {
        GitOperations(rootDir.get(), project.properties, logger).preReleaseGitCommit(releaseVersion.get())
    }
}
