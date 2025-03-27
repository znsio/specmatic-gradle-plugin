package io.specmatic.gradle.jar.publishing

import io.specmatic.gradle.extensions.ObfuscationFeature
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.obfuscate.ProguardTask
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar

internal const val OBFUSCATE_JAR_TASK = "obfuscateJar"

internal fun Project.createObfuscatedOriginalJar(proguardExtraArgs: MutableList<String?>) {
    val distributionFlavor = project.specmaticExtension().projectConfigurations[this]!!
    if (distributionFlavor !is ObfuscationFeature) {
        throw GradleException("Obfuscation feature is not enabled on $this")
    }

    project.pluginInfo("Installing obfuscation hook on $project")
    project.plugins.withType(JavaPlugin::class.java) {
        project.pluginInfo("Configuring obfuscation for on $project")

        val obfuscateJarInternalTask = project.tasks.register("obfuscateJarInternal", ProguardTask::class.java) {
            val jarTask = project.tasks.jar.get()
            dependsOn(jarTask)
            inputJar = jarTask.archiveFile.get().asFile
            outputJar = project.file("${project.layout.buildDirectory.get().asFile}/tmp/obfuscated-internal.jar")

            appendArgsToFile(*proguardExtraArgs.filterNotNull().toTypedArray())
        }

        // Jar tasks automatically register as maven publication, so we "copy" the proguard jar into another one
        project.tasks.register(OBFUSCATE_JAR_TASK, Jar::class.java) {
            group = "build"
            description = "Run obfuscation on the output of the `jar` task"

            dependsOn(obfuscateJarInternalTask)
            val obfuscatedTempJar = obfuscateJarInternalTask.get().outputJar!!

            inputs.file(obfuscatedTempJar)

            from(project.zipTree(obfuscatedTempJar))
            archiveClassifier.set("obfuscated")

            obfuscateJarInternalTask.get().finalizedBy(this)
            project.tasks.getByName("assemble").dependsOn(this)
        }
    }

}
