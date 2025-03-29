package io.specmatic.gradle.jar.massage

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin.Companion.CONFIGURATION_NAME
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

internal inline val ConfigurationContainer.shadow: NamedDomainObjectProvider<Configuration>
    get() = named(CONFIGURATION_NAME)

internal inline val Project.publishing: PublishingExtension
    get() = extensions.getByType(PublishingExtension::class.java)

internal fun Project.mavenPublications(action: Action<MavenPublication>) {
    publishing.publications.withType(MavenPublication::class.java).configureEach(action)
}

internal inline val TaskContainer.jar: TaskProvider<Jar>
    get() = named("jar", Jar::class.java)

//internal inline val TaskContainer.obfuscateJarTask: TaskProvider<Jar>
//    get() = named(OBFUSCATE_JAR_TASK, Jar::class.java)

//internal inline val TaskContainer.unobfuscatedShadowJarTask: TaskProvider<ShadowJar>
//    get() = named(UNOBFUSCATED_SHADOW_JAR, ShadowJar::class.java)

internal fun Project.applyToRootProjectOrSubprojects(block: Project.() -> Unit) {
    if (subprojects.isEmpty()) {
        // apply on self
        block()
    } else {
        subprojects {
            // apply on eachSubproject
            block()
        }
    }
}
