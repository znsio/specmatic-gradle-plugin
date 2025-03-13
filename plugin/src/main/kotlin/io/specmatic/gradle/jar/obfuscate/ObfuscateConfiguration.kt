package io.specmatic.gradle.jar.obfuscate

import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.pluginDebug
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

const val OBFUSCATE_JAR_TASK = "obfuscateJar"

private const val OBFUSCATE_JAR_INTERNAL = "obfuscateJarInternal"

class ObfuscateConfiguration(val project: Project, val projectConfiguration: ProjectConfiguration) {
    init {
        configureProguard()
    }

    private fun configureProguard() {
        pluginDebug("Installing obfuscation hook on $project")
        project.pluginManager.withPlugin("java") {
            pluginDebug("Configuring obfuscation for on $project")
            val obfuscateJarInternalTask = project.tasks.register(OBFUSCATE_JAR_INTERNAL, ProguardTask::class.java) {
                val jarTask = project.tasks.jar.get()
                dependsOn(jarTask)
                inputJar = jarTask.archiveFile.get().asFile
                outputJar = project.file("${project.layout.buildDirectory.get().asFile}/tmp/obfuscated-internal.jar")

                args(*projectConfiguration.proguardExtraArgs.filterNotNull().toTypedArray())
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
