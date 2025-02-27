package io.specmatic.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import proguard.gradle.ProGuardTask

class ProguardConfiguration(project: Project) {
    init {
        project.afterEvaluate {
            val specmaticExtension =
                findSpecmaticExtension(project) ?: throw GradleException("SpecmaticGradleExtension not found")
            val obfuscatedProjects = specmaticExtension.obfuscatedProjects
            obfuscatedProjects.forEach(::configureProguard)
        }
    }

    private fun configureProguard(project: Project) {
        project.afterEvaluate {
            val proguardTaskProvider = project.tasks.register("proguard", ProGuardTask::class.java)

            project.tasks.withType(Jar::class.java).configureEach {
                archiveClassifier.set("original")
                finalizedBy(proguardTaskProvider)
            }

            val jarTask = project.tasks.getByName("jar") as Jar
            val jarTaskFile = jarTask.archiveFile.get().asFile

            proguardTaskProvider.configure {
                group = "build"
                description = "Run Proguard on the output of the `jar` task"
                dependsOn("jar")
                injars(jarTaskFile)
                outjars(jarTaskFile.path.replace("-${jarTask.archiveClassifier.get()}.jar", "-obfuscated.jar"))
                configuration(project.file("proguard.conf"))
            }
        }
    }
}
