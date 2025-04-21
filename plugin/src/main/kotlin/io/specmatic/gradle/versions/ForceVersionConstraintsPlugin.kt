package io.specmatic.gradle.versions

import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project


class ForceVersionConstraintsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val printedReplacementLogs = mutableListOf<String>()

        target.afterEvaluate {
            val versionReplacements = target.specmaticExtension().versionReplacements
            target.configurations.all {
                resolutionStrategy.eachDependency {
                    (REPLACEMENTS + versionReplacements).forEach { (source, replacement) ->
                        val (sourceGroup, sourceName) = source.split(":")
                        if (requested.group == sourceGroup && requested.name == sourceName) {
                            if (!printedReplacementLogs.contains("${requested.group}:${requested.name}")) {
                                target.pluginInfo("Replacing $source with $replacement")
                            } else {
                                printedReplacementLogs.add("${requested.group}:${requested.name}")
                            }
                            useTarget(replacement)
                            because("Overridden by plugin")
                        }
                    }
                }
            }
        }
    }

    companion object {
        val REPLACEMENTS = mapOf(
            "javax.validation:validation-api" to "jakarta.validation:jakarta.validation-api:3.1.1",
            "dk.brics.automaton:automaton" to "dk.brics:automaton:1.12-4",
        )
    }
}
