package io.specmatic.gradle

import org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin
import org.gradle.api.Project

internal class ConfigureTaskInfo(project: Project) {
    init {
        println("Configuring task info plugin on $project")
        project.plugins.apply(GradleTaskInfoPlugin::class.java)
    }
}