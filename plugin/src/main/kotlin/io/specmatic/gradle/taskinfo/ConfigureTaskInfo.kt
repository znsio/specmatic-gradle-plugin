package io.specmatic.gradle.taskinfo

import io.specmatic.gradle.pluginDebug
import org.barfuin.gradle.taskinfo.GradleTaskInfoPlugin
import org.gradle.api.Project

internal class ConfigureTaskInfo(project: Project) {
    init {
        pluginDebug("Configuring task info plugin on $project")
        project.plugins.apply(GradleTaskInfoPlugin::class.java)
    }
}