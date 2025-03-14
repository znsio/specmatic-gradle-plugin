package io.specmatic.gradle.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.obfuscate.OBFUSCATE_JAR_TASK
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.jar.JarFile

const val SHADOW_ORIGINAL_JAR = "shadowOriginalJar"
const val SHADOW_OBFUSCATED_JAR = "shadowObfuscatedJar"

class ShadowJarsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.afterEvaluate {
            val specmaticExtension = target.specmaticExtension()
            val projectConfiguration = specmaticExtension.projectConfigurations[target]
            if (projectConfiguration?.shadowEnabled == true) {
                configureShadowJars(target, projectConfiguration)
            }
        }
    }

    private fun configureShadowJars(project: Project, projectConfiguration: ProjectConfiguration) {
        project.plugins.withType(JavaPlugin::class.java) {
            project.pluginInfo("Configuring shadow jar on $project")
            shadowOriginalJar(project, projectConfiguration)
            shadowObfuscatedJar(project, projectConfiguration)
        }
    }

    private fun shadowObfuscatedJar(project: Project, projectConfiguration: ProjectConfiguration) {
        val obfuscateJarTask = project.tasks.findByName(OBFUSCATE_JAR_TASK) as Jar? ?: return
        val jarTask = project.tasks.jar.get()
        project.pluginInfo("Created task $SHADOW_OBFUSCATED_JAR on $project")
        val shadowObfuscatedJarTask = project.tasks.register(SHADOW_OBFUSCATED_JAR, ShadowJar::class.java) {
            group = "build"
            description = "Shadow the obfuscated jar"

            dependsOn(obfuscateJarTask)
            dependsOn(jarTask) // since we use manifest from jarTask, we make this explicit, although obfuscateJarTask depends on jarTask
            project.tasks.getByName("assemble").dependsOn(this)

            archiveClassifier.set("all-obfuscated")

            from(project.provider { project.zipTree(obfuscateJarTask.archiveFile) })

            configureCommonShadowConfigs(jarTask, project, projectConfiguration)
        }

        // any extra config specified by the user
        projectConfiguration.shadowAction?.let {
            project.pluginInfo("Applying custom shadow jar configuration on $project")
            shadowObfuscatedJarTask.configure(it)
        }
    }

    private fun shadowOriginalJar(project: Project, projectConfiguration: ProjectConfiguration) {
        val jarTask = project.tasks.jar.get()
        project.pluginInfo("Created task $SHADOW_ORIGINAL_JAR on $project")
        val shadowOriginalJarTask = project.tasks.register(SHADOW_ORIGINAL_JAR, ShadowJar::class.java) {
            group = "build"
            description = "Shadow the original jar"

            dependsOn(jarTask)
            project.tasks.getByName("assemble").dependsOn(this)

            archiveClassifier.set("all-original")

            from(project.provider { project.zipTree(jarTask.archiveFile) })

            configureCommonShadowConfigs(jarTask, project, projectConfiguration)
        }

        // any extra config specified by the user
        projectConfiguration.shadowAction?.let {
            project.pluginInfo("Applying custom shadow jar configuration on $project")
            shadowOriginalJarTask.configure(it)
        }
    }
}

fun ShadowJar.configureCommonShadowConfigs(jarTask: Jar, project: Project, projectConfiguration: ProjectConfiguration) {
    val runtimeClasspath = project.configurations.findByName("runtimeClasspath")

    maybeRelocateIfConfigured(project, projectConfiguration)

    manifest.inheritFrom(jarTask.manifest)
    configurations.set(listOf(runtimeClasspath))

    mergeServiceFiles()

    exclude(
        "META-INF/INDEX.LIST",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/versions/**/module-info.class",
        "module-info.class"
    )

    dependencies {
        exclude(dependency(project.dependencies.gradleApi()))
    }
}

private fun ShadowJar.maybeRelocateIfConfigured(project: Project, projectConfiguration: ProjectConfiguration) {
    val shadowPrefix = projectConfiguration.shadowPrefix
    val runtimeClasspath = project.configurations.findByName("runtimeClasspath")

    if (!shadowPrefix.isNullOrBlank()) {
        val excludePackages = if (projectConfiguration.shadowApplication) listOf("java", "javax")
        else listOf("kotlin", "org/jetbrains", "org/intellij/lang/annotations", "java", "javax")
        val runtimeClasspathFiles = runtimeClasspath?.files.orEmpty()

        excludePackages.forEach { this.exclude("${it}/**") }

        val packagesToRelocate = extractPackagesInJars(runtimeClasspathFiles, excludePackages)

        packagesToRelocate.forEach { eachPackage ->
            project.pluginInfo("Relocating package: $eachPackage to $shadowPrefix/$eachPackage")
            relocate(eachPackage, "$shadowPrefix.$eachPackage")
        }
    }
}

private fun extractPackagesInJars(runtimeClasspathFiles: Set<File>, excludePackages: List<String>): MutableSet<String> {
    val packagesToRelocate = mutableSetOf<String>()
    runtimeClasspathFiles.forEach { eachFile ->
        if (eachFile.name.lowercase().endsWith(".jar")) {
            val jarInputStream = JarFile(eachFile)
            jarInputStream.entries().asSequence().forEach { entry ->
                val entryName = entry.name
                if (shouldRelocatePackage(entryName, excludePackages)) {
                    val packageName = entryName.substring(0, entryName.lastIndexOf('/'))
                    packagesToRelocate.add(packageName)
                }
            }
        }
    }
    return packagesToRelocate
}

private fun shouldRelocatePackage(entryName: String, excludePackages: List<String>): Boolean =
    entryName.endsWith(".class") && entryName.contains("/") && excludePackages.none {
        entryName.startsWith(
            "${it}/"
        )
    } && !entryName.startsWith("META-INF/")
