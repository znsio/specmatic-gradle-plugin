package io.specmatic.gradle

import net.researchgate.release.ReleasePlugin
import org.gradle.api.Project

internal class ConfigureReleases(project: Project) {
    init {
        println("Configuring release plugin on $project")
        project.plugins.apply(ReleasePlugin::class.java)
    }
}