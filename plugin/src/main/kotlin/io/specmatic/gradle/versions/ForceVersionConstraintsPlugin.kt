package io.specmatic.gradle.versions

import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Plugin
import org.gradle.api.Project

class ForceVersionConstraintsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // replace javax.validation:validation-api with jakarta.validation:jakarta.validation-api
        target.configurations.all {
            resolutionStrategy.eachDependency {

                val replacements = mapOf(
                    "javax.validation:validation-api" to "jakarta.validation:jakarta.validation-api:3.1.1",
                    "dk.brics.automaton:automaton" to "dk.brics:automaton:1.12-4"
                )

                replacements.forEach { (source, replacement) ->
                    val (sourceGroup, sourceName) = source.split(":")
                    if (requested.group == sourceGroup && requested.name == sourceName) {
                        target.pluginInfo("Replacing $source with $replacement")
                        useTarget(replacement)
                        because("Overridden by plugin")
                    }
                }
            }
        }
    }
}
