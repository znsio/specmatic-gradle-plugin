package io.specmatic.gradle.jar.massage

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.CONFIGURATION_NAME
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

internal inline val ConfigurationContainer.shadow: NamedDomainObjectProvider<Configuration>
    get() = named(CONFIGURATION_NAME)

internal inline val TaskContainer.jar: TaskProvider<Jar>
    get() = named("jar", Jar::class.java)
