package io.specmatic.gradle.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.obfuscate.OBFUSCATE_JAR_TASK
import io.specmatic.gradle.pluginDebug
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.jar.JarFile

const val SHADOW_ORIGINAL_JAR = "shadowOriginalJar"
const val SHADOW_OBFUSCATED_JAR = "shadowObfuscatedJar"

class ShadowJarConfiguration(project: Project, projectConfiguration: ProjectConfiguration) {
    init {
        configureShadowJar(project, projectConfiguration.shadowPrefix!!, projectConfiguration.shadowAction)
    }

    private fun configureShadowJar(project: Project, shadowPrefix: String, shadowAction: Action<ShadowJar>?) {
        project.pluginManager.withPlugin("java") {
            pluginDebug("Configuring shadow jar on $project")
            shadowOriginalJar(project, shadowPrefix, shadowAction)
            shadowObfuscatedJar(project, shadowPrefix, shadowAction)
        }
    }

    private fun shadowObfuscatedJar(project: Project, shadowPrefix: String, shadowJarConfig: Action<ShadowJar>?) {
        val obfuscateJarTask = project.tasks.findByName(OBFUSCATE_JAR_TASK) as Jar? ?: return
        val jarTask = project.jarTaskProvider().get()
        pluginDebug("Created task $SHADOW_OBFUSCATED_JAR on $project")
        val shadowObfuscatedJarTask = project.tasks.register(SHADOW_OBFUSCATED_JAR, ShadowJar::class.java) {
            group = "build"
            description = "Shadow the obfuscated jar"

            dependsOn(obfuscateJarTask)
            dependsOn(jarTask) // since we use manifest from jarTask, we make this explicit, although obfuscateJarTask depends on jarTask
            project.tasks.getByName("assemble").dependsOn(this)

            archiveClassifier.set("all-obfuscated")

            from(project.zipTree(obfuscateJarTask.archiveFile))

            configureShadowJar(jarTask, project, shadowPrefix)
        }

        // any extra config specified by the user
        shadowJarConfig?.let {
            pluginDebug("Applying custom shadow jar configuration on $project")
            shadowObfuscatedJarTask.configure(it)
        }
    }

    private fun shadowOriginalJar(project: Project, shadowPrefix: String, shadowJarConfig: Action<ShadowJar>?) {
        val jarTask = project.jarTaskProvider().get()
        pluginDebug("Created task $SHADOW_ORIGINAL_JAR on $project")
        val shadowOriginalJarTask = project.tasks.register(SHADOW_ORIGINAL_JAR, ShadowJar::class.java) {
            group = "build"
            description = "Shadow the original jar"

            dependsOn(jarTask)
            project.tasks.getByName("assemble").dependsOn(this)

            archiveClassifier.set("all-original")

            from(project.zipTree(jarTask.archiveFile))
            manifest.inheritFrom(jarTask.manifest)

            configureShadowJar(jarTask, project, shadowPrefix)
        }

        // any extra config specified by the user
        shadowJarConfig?.let {
            pluginDebug("Applying custom shadow jar configuration on $project")
            shadowOriginalJarTask.configure(it)
        }
    }
}

fun Project.jarTaskProvider() = tasks.named("jar", Jar::class.java)
fun ShadowJar.configureShadowJar(jarTask: Jar, project: Project, shadowPrefix: String) {
    val runtimeClasspath = project.configurations.findByName("runtimeClasspath")
    val excludePackages = listOf("kotlin", "org/jetbrains", "org/intellij/lang/annotations", "java", "javax")


    val runtimeClasspathFiles = runtimeClasspath?.files.orEmpty()

    excludePackages.forEach { this.exclude("${it}/**") }

    val packagesToRelocate = extractPackagesInJars(runtimeClasspathFiles, excludePackages)

    packagesToRelocate.forEach { eachPackage ->
        pluginDebug("Relocating package: $eachPackage to ${shadowPrefix}/$eachPackage")
        relocate(eachPackage, "${shadowPrefix}.$eachPackage")
    }

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
