package io.specmatic.gradle.detekt

import io.gitlab.arturbosch.detekt.CONFIGURATION_DETEKT_PLUGINS
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.get

class SpecmaticDetektPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(DetektPlugin::class.java)
        target.plugins.withType(DetektPlugin::class.java) {
            target.extensions.getByType(DetektExtension::class.java).apply {
                parallel = true
                buildUponDefaultConfig = true
                config.setFrom(target.rootProject.files("detekt.yml"))

                target.configurations[CONFIGURATION_DETEKT_PLUGINS].dependencies.add(
                    target.dependencies.create("io.gitlab.arturbosch.detekt:detekt-formatting:$toolVersion"),
                )
            }
        }
    }
}
