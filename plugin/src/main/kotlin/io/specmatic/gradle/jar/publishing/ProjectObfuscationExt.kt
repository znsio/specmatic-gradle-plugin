package io.specmatic.gradle.jar.publishing

import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.obfuscate.ProguardTask
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

internal const val OBFUSCATE_JAR_TASK = "obfuscateJar"

internal fun Project.createObfuscatedOriginalJar(proguardExtraArgs: MutableList<String?>): TaskProvider<Jar> {
    project.pluginInfo("Installing obfuscation hook on $project")
    project.pluginInfo("Configuring obfuscation for on $project")

    val obfuscateJarInternalTask = project.tasks.register("obfuscateJarInternal", ProguardTask::class.java) {
        val jarTask = project.tasks.jar.get()
        dependsOn(jarTask)
        inputJar = jarTask.archiveFile.get().asFile
        outputJar = project.file("${project.layout.buildDirectory.get().asFile}/tmp/obfuscated-internal.jar")

        appendProguardArgs(*proguardExtraArgs.filterNotNull().toTypedArray())
    }

    // Jar tasks automatically register as maven publication, so we "copy" the proguard jar into another one
    val obfuscateJarTask = project.tasks.register(OBFUSCATE_JAR_TASK, Jar::class.java) {
        project.pluginInfo("Created task $path on $project")

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

    return obfuscateJarTask
}
