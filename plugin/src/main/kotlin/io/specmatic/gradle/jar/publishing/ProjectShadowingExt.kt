package io.specmatic.gradle.jar.publishing

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.jar.massage.jar
import io.specmatic.gradle.jar.massage.obfuscateJarTask
import io.specmatic.gradle.jar.massage.shadow
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.jar.JarFile

internal const val UNOBFUSCATED_SHADOW_JAR = "unobfuscatedShadowJar"
private const val SHADOW_OBFUSCATED_JAR = "shadowObfuscatedJar"

internal fun Project.createUnobfuscatedShadowJar(
    shadowActions: MutableList<Action<ShadowJar>>,
    shadowPrefix: String,
    isApplication: Boolean
): TaskProvider<ShadowJar> {
    val jarTask = project.tasks.jar
    project.pluginInfo("Created task $UNOBFUSCATED_SHADOW_JAR on $project")

    return project.tasks.register<ShadowJar?>(UNOBFUSCATED_SHADOW_JAR, ShadowJar::class.java) {
        group = "build"
        description = "Shadow the original jar"

        dependsOn(jarTask)
        project.tasks.getByName("assemble").dependsOn(this)

        archiveClassifier.set("all-unobfuscated")

        from(project.provider { project.zipTree(jarTask.get().archiveFile) })

        configureCommonShadowConfigs(jarTask, project, shadowPrefix, isApplication)
        applyProjectSpecifiedConfigurations(this, shadowActions)
    }
}

internal fun Project.createObfuscatedShadowJar(
    shadowActions: MutableList<Action<ShadowJar>>,
    shadowPrefix: String,
    isApplication: Boolean
): TaskProvider<ShadowJar> {
    val obfuscateJarTask = project.tasks.obfuscateJarTask
    val jarTask = project.tasks.jar

    project.pluginInfo("Created task $SHADOW_OBFUSCATED_JAR on $project")
    return project.tasks.register(SHADOW_OBFUSCATED_JAR, ShadowJar::class.java) {
        group = "build"
        description = "Shadow the obfuscated jar"

        dependsOn(obfuscateJarTask)
        dependsOn(jarTask) // since we use manifest from jarTask, we make this explicit, although obfuscateJarTask depends on jarTask
        project.tasks.getByName("assemble").dependsOn(this)

        archiveClassifier.set("all-obfuscated")

        from(project.provider { project.zipTree(obfuscateJarTask.get().archiveFile) })

        configureCommonShadowConfigs(jarTask, project, shadowPrefix, isApplication)
        applyProjectSpecifiedConfigurations(this, shadowActions)
    }

}


private fun Project.applyProjectSpecifiedConfigurations(
    shadowJarTask: ShadowJar, shadowActions: MutableList<Action<ShadowJar>>
) {
    shadowActions.forEach {
        project.pluginInfo("Applying custom shadow jar configuration on $project")
        it.execute(shadowJarTask)
    }
}


internal fun Project.applyShadowConfigs() {
    plugins.withType(JavaPlugin::class.java) {
        pluginInfo("Applying shadow configurations to project $name")
        val shadowConfiguration = configurations.create("shadow")

        val shadowRuntimeElements = configurations.create(SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
            extendsFrom(shadowConfiguration)
            isCanBeConsumed = true
            isCanBeResolved = false
        }

        configurations.named("compileClasspath") {
            extendsFrom(shadowConfiguration)
        }

        configurations.named("testImplementation") {
            extendsFrom(shadowConfiguration)
        }

        configurations.named("testRuntimeOnly") {
            extendsFrom(shadowConfiguration)
        }

        val softwareComponentFactory = (project as ProjectInternal).services.get(SoftwareComponentFactory::class.java)
        val shadowComponent = softwareComponentFactory.adhoc(ShadowBasePlugin.COMPONENT_NAME)
        project.components.add(shadowComponent)
        shadowComponent.addVariantsFromConfiguration(shadowRuntimeElements) {
            mapToMavenScope("runtime")
        }

        tasks.jar.configure {
            from(provider { configurations.shadow.get().files.map { zipTree(it) } })
        }
    }

}


private fun ShadowJar.maybeRelocateIfConfigured(project: Project, shadowPrefix: String, isApplication: Boolean) {
    val runtimeClasspath = project.configurations.findByName("runtimeClasspath")

    if (shadowPrefix.isNotBlank()) {
        val excludePackages = if (isApplication) listOf("java", "javax")
        else listOf("kotlin", "org/jetbrains", "org/intellij/lang/annotations", "java", "javax")
        var thisShadowJarTask = this

        // we do this in doFirst so that we can lazily evaluate the runtimeClasspath
        doFirst {
            val runtimeClasspathFiles = runtimeClasspath?.files.orEmpty()

            excludePackages.forEach { thisShadowJarTask.exclude("${it}/**") }

            val packagesToRelocate = extractPackagesInJars(runtimeClasspathFiles, excludePackages)

            packagesToRelocate.forEach { eachPackage ->
                project.pluginInfo("Relocating package: $eachPackage to $shadowPrefix/$eachPackage")
                relocate(eachPackage, "$shadowPrefix.$eachPackage")
            }
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


internal fun ShadowJar.configureCommonShadowConfigs(
    jarTask: TaskProvider<Jar>,
    project: Project,
    shadowPrefix: String,
    isApplication: Boolean,
) {
    val runtimeClasspath = project.configurations.findByName("runtimeClasspath")

    maybeRelocateIfConfigured(project, shadowPrefix, isApplication)

    manifest.inheritFrom(jarTask.get().manifest)
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
