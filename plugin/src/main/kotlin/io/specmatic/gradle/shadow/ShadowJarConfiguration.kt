package io.specmatic.gradle.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.obfuscate.OBFUSCATE_JAR_TASK
import io.specmatic.gradle.pluginDebug
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

const val SHADOW_ORIGINAL_JAR = "shadowOriginalJar"
const val SHADOW_OBFUSCATED_JAR = "shadowObfuscatedJar"

class ShadowJarConfiguration(project: Project, projectConfiguration: ProjectConfiguration) {
    init {
        configureShadowJar(project, projectConfiguration.shadowAction)
    }

    private fun configureShadowJar(project: Project, shadowAction: Action<ShadowJar>?) {
        project.pluginManager.withPlugin("java") {
            pluginDebug("Configuring shadow jar on $project")
            shadowOriginalJar(project, shadowAction)
            shadowObfuscatedJar(project, shadowAction)
        }
    }

    private fun shadowObfuscatedJar(project: Project, shadowJarConfig: Action<ShadowJar>?) {
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

            configureShadowJar(jarTask, project)
        }

        // any extra config specified by the user
        shadowJarConfig?.let {
            pluginDebug("Applying custom shadow jar configuration on $project")
            shadowObfuscatedJarTask.configure(it)
        }
    }

    private fun shadowOriginalJar(project: Project, shadowJarConfig: Action<ShadowJar>?) {
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

            configureShadowJar(jarTask, project)
        }

        // any extra config specified by the user
        shadowJarConfig?.let {
            pluginDebug("Applying custom shadow jar configuration on $project")
            shadowOriginalJarTask.configure(it)
        }
    }
}

fun Project.jarTaskProvider() = tasks.named("jar", Jar::class.java)
fun ShadowJar.configureShadowJar(jarTask: Jar, project: Project) {
    manifest.inheritFrom(jarTask.manifest)
    configurations.set(
        listOf(
            project.configurations.findByName("runtimeClasspath") ?: project.configurations.findByName("runtime")
        )
    )
    mergeServiceFiles()

    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")

    dependencies {
        exclude(dependency(project.dependencies.gradleApi()))
    }
}
