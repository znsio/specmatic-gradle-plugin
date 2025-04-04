package io.specmatic.gradle.extensions

import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

internal fun Project.baseSetup() {
    project.plugins.apply(JavaPlugin::class.java)
    project.plugins.withType(JavaPlugin::class.java) {
        project.configurations.named("implementation") {
            project.pluginInfo("Adding 'org.jetbrains.kotlin:kotlin-stdlib:${project.specmaticExtension().kotlinVersion}' to implementation configuration")
            this.dependencies.add(project.dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:${project.specmaticExtension().kotlinVersion}"))
        }
    }

    project.configureSigning()
    project.configurePublishing()
}
