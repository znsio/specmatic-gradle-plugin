package io.specmatic.gradle

import net.researchgate.release.ReleasePlugin
import org.gradle.api.Project

internal class ConfigureReleases(project: Project) {
    init {
        project.plugins.apply(ReleasePlugin::class.java)
    }
}