package io.specmatic.gradle

import org.eclipse.jgit.util.io.NullOutputStream
import org.gradle.api.Project
import org.gradle.api.tasks.Exec

class ConfigureExecTask(project: Project) {
    init {
        project.allprojects.forEach(::configure)
    }

    fun configure(project: Project) {
        project.afterEvaluate {
            project.tasks.withType(Exec::class.java) {
                if (project.logger.isInfoEnabled) {
                    standardOutput = System.out
                    errorOutput = System.err
                } else {
                    standardOutput = NullOutputStream.INSTANCE
                    errorOutput = NullOutputStream.INSTANCE
                }
            }
        }
    }
}