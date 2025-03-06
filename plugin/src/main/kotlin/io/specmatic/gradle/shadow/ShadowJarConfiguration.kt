package io.specmatic.gradle.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.specmatic.gradle.findSpecmaticExtension
import io.specmatic.gradle.obfuscate.OBFUSCATE_JAR_TASK
import io.specmatic.gradle.pluginDebug
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

const val SHADOW_ORIGINAL_JAR = "shadowOriginalJar"
const val SHADOW_OBFUSCATED_JAR = "shadowObfuscatedJar"

class ShadowJarConfiguration(project: Project) {
    init {
        val specmaticExtension =
            findSpecmaticExtension(project) ?: throw GradleException("SpecmaticGradleExtension not found")

        val shadowJarProjects = specmaticExtension.shadowConfigurations
        project.afterEvaluate {
            shadowJarProjects.forEach(::configureShadowJar)
        }
    }

    private fun configureShadowJar(project: Project, shadowJarConfig: Action<ShadowJar>?) {
        project.pluginManager.withPlugin("java") {
            pluginDebug("Configuring shadow jar on $project")
            shadowOriginalJar(project, shadowJarConfig)
            shadowObfuscatedJar(project, shadowJarConfig)
        }
    }

    private fun shadowObfuscatedJar(project: Project, shadowJarConfig: Action<ShadowJar>?) {
        val obfuscateJarTask = project.tasks.findByName(OBFUSCATE_JAR_TASK) as Jar? ?: return
        val jarTask = project.jarTaskProvider().get()
        val shadowObfuscatedJarTask = project.tasks.register(SHADOW_OBFUSCATED_JAR, ShadowJar::class.java)

        project.tasks.getByName("assemble").dependsOn(shadowObfuscatedJarTask)

        shadowObfuscatedJarTask.configure {
            group = "build"
            description = "Shadow the obfuscated jar"

            dependsOn(obfuscateJarTask)
            dependsOn(jarTask) // since we use manifest from jarTask, we make this explicit, although obfuscateJarTask depends on jarTask
            archiveClassifier.set("all-obfuscated")
            from(obfuscateJarTask.archiveFile)

            manifest.inheritFrom(jarTask.manifest)
            configurations = listOf(
                project.configurations.findByName("runtimeClasspath") ?: project.configurations.findByName("runtime")
            )
            exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
            dependencies {
                exclude(dependency(project.dependencies.gradleApi()))
            }
        }

        // any extra config specified by the user
        shadowJarConfig?.let { shadowObfuscatedJarTask.configure(it) }
    }

    private fun shadowOriginalJar(project: Project, shadowJarConfig: Action<ShadowJar>?) {
        val jarTask = project.jarTaskProvider().get()
        val shadowOriginalJarTask = project.tasks.register(SHADOW_ORIGINAL_JAR, ShadowJar::class.java)

        project.tasks.getByName("assemble").dependsOn(shadowOriginalJarTask)

        shadowOriginalJarTask.configure {
            group = "build"
            description = "Shadow the original jar"

            dependsOn(jarTask)
            archiveClassifier.set("all-original")
            from(jarTask.archiveFile)

            configureShadowJar(jarTask, project)
        }

        // any extra config specified by the user
        shadowJarConfig?.let { shadowOriginalJarTask.configure(it) }
    }
}

fun Project.jarTaskProvider() = tasks.named("jar", Jar::class.java)
fun ShadowJar.configureShadowJar(jarTask: Jar, project: Project) {
    manifest.inheritFrom(jarTask.manifest)
    configurations = listOf(
        project.configurations.findByName("runtimeClasspath") ?: project.configurations.findByName("runtime")
    )
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    dependencies {
        exclude(dependency(project.dependencies.gradleApi()))
    }
}