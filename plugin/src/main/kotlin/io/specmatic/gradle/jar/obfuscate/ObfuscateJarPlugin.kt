package io.specmatic.gradle.jar.obfuscate

import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar

const val OBFUSCATE_JAR_TASK = "obfuscateJar"

private const val OBFUSCATE_JAR_INTERNAL = "obfuscateJarInternal"

abstract class ObfuscateJarPlugin() : Plugin<Project> {

    override fun apply(target: Project) {
        target.afterEvaluate {
            val projectConfiguration = project.specmaticExtension().projectConfigurations[target]
            if (projectConfiguration?.proguardEnabled == true) {
                configureProguard(target, projectConfiguration)
            }
        }
    }

    private fun configureProguard(project: Project, projectConfiguration: ProjectConfiguration) {
        project.pluginInfo("Installing obfuscation hook on $project")
        project.plugins.withType(JavaPlugin::class.java) {
            project.pluginInfo("Configuring obfuscation for on $project")

            val obfuscateJarInternalTask = project.tasks.register(OBFUSCATE_JAR_INTERNAL, ProguardTask::class.java) {
                val jarTask = project.tasks.jar.get()
                dependsOn(jarTask)
                inputJar = jarTask.archiveFile.get().asFile
                outputJar = project.file("${project.layout.buildDirectory.get().asFile}/tmp/obfuscated-internal.jar")

                appendArgsToFile(*projectConfiguration.proguardExtraArgs.filterNotNull().toTypedArray())
            }

            // Jar tasks automatically register as maven publication, so we "copy" the proguard jar into another one
            val obfuscateJarTask = project.tasks.register(OBFUSCATE_JAR_TASK, Jar::class.java) {
                group = "build"
                description = "Run obfuscation on the output of the `jar` task"

                dependsOn(obfuscateJarInternalTask)
                val obfuscatedTempJar = obfuscateJarInternalTask.get().outputJar!!

                inputs.file(obfuscatedTempJar)

                from(project.zipTree(obfuscatedTempJar))
                archiveClassifier.set("obfuscated")
            }
            obfuscateJarInternalTask.get().finalizedBy(obfuscateJarTask)

            project.tasks.getByName("assemble").dependsOn(obfuscateJarTask)
        }
    }

}
