package io.specmatic.gradle.release

import org.gradle.api.DefaultTask
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
        GitOperations(rootDir.get(), project.properties, logger).apply {
            assertMainBranch()
            assertRepoNotDirty()
            assertNoIncomingOrOutgoingChanges()
        }

    }

}
