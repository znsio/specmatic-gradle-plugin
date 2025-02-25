package io.specmatic.gradle

import org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin
import org.gradle.api.Project

internal class ConfigureTaskInfo(private val project: Project) {
    init {
        project.plugins.apply(GradleTaskInfoPlugin::class.java)
    }
}