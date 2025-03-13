package io.specmatic.gradle.jar.massage

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.CONFIGURATION_NAME
import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.findSpecmaticExtension
import io.specmatic.gradle.jar.obfuscate.ObfuscateConfiguration
import io.specmatic.gradle.jar.publishing.ConfigurePublications
import io.specmatic.gradle.shadow.ShadowJarConfiguration
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

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

internal inline val ConfigurationContainer.shadow: NamedDomainObjectProvider<Configuration>
    get() = named(CONFIGURATION_NAME)

internal inline val TaskContainer.jar: TaskProvider<Jar>
    get() = named("jar", Jar::class.java)
