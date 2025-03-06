package io.specmatic.gradle.releases

import io.specmatic.gradle.pluginDebug
import net.researchgate.release.ReleasePlugin
import org.gradle.api.Project

internal class ConfigureReleases(project: Project) {
    init {
        pluginDebug("Configuring release plugin on $project")
        project.plugins.apply(ReleasePlugin::class.java)
    }
}