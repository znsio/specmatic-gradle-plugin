package io.specmatic.gradle.release

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault
abstract class PostReleaseBump : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val postReleaseVersion: Property<String>

    @TaskAction
    fun postReleaseBump() {
        GitOperations(rootDir.get(), project.properties, logger).apply {
            postReleaseBump(postReleaseVersion.get())
        }
    }
}
