package io.specmatic.gradle.jar.massage

import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.findSpecmaticExtension
import io.specmatic.gradle.jar.publishing.ConfigurePublications
import io.specmatic.gradle.jar.obfuscate.ObfuscateConfiguration
import io.specmatic.gradle.shadow.ShadowJarConfiguration
import org.gradle.api.GradleException
import org.gradle.api.Project

class ObfuscateShadowAndPublish(rootProject: Project) {
    init {
        rootProject.afterEvaluate {
            val specmaticExtension =
                findSpecmaticExtension(rootProject) ?: throw GradleException("SpecmaticGradleExtension not found")

            specmaticExtension.projectConfigurations.forEach { (subproject, projectConfiguration) ->
                subproject.afterEvaluate {
                    configure(projectConfiguration, subproject)
                }
            }
        }
    }

    private fun configure(
        projectConfiguration: ProjectConfiguration, subproject: Project
    ) {
        // obfuscation needs to happen before shadowing!
        if (projectConfiguration.proguardEnabled) {
            ObfuscateConfiguration(subproject, projectConfiguration)
        }

        if (projectConfiguration.shadowAction != null) {
            ShadowJarConfiguration(subproject, projectConfiguration)
        }

        // publishing needs to happen last!
        if (projectConfiguration.publicationEnabled) {
            ConfigurePublications(subproject, projectConfiguration)
        }
    }
}
