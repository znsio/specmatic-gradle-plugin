package io.specmatic.gradle.versions

import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradlex.jvm.dependency.conflict.detection.JvmDependencyConflictDetectionPlugin
import org.gradlex.jvm.dependency.conflict.resolution.JvmDependencyConflictResolutionPlugin
import org.gradlex.jvm.dependency.conflict.resolution.JvmDependencyConflictsExtension

class ForceVersionConstraintsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(JvmDependencyConflictDetectionPlugin::class.java)
        project.plugins.apply(JvmDependencyConflictResolutionPlugin::class.java)

        project.plugins.withType(JvmDependencyConflictResolutionPlugin::class.java) {
            project.extensions.configure(JvmDependencyConflictsExtension::class.java) {
                replacements(project).forEach { (source, replacement) ->
                    val capabilityId = "io.specmatic.capability:${source.replace(':', '_')}"

                    conflictResolution.selectHighestVersion(capabilityId)

                    patch.module(source).addCapability(capabilityId)
                    val (replacementGroup, replacementArtifact) = replacement.split(":")
                    patch.module("$replacementGroup:$replacementArtifact").addCapability(capabilityId)
                }
            }
        }
    }

    private fun replacements(project: Project): Map<String, String> {
        val versionReplacements = project.specmaticExtension().versionReplacements
        return REPLACEMENTS + versionReplacements
    }

    companion object {
        val REPLACEMENTS = mapOf(
            "dk.brics.automaton:automaton" to "dk.brics:automaton:1.12-4",
        )
    }
}
