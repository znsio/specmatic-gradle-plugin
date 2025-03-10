package io.specmatic.gradle.obfuscate

import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.pluginDebug
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.jvm.tasks.Jar
import java.io.File

const val OBFUSCATE_JAR_TASK = "obfuscateJar"

open class ProguardTask : JavaExec() {
    init {
        // since proguard is GPL, we avoid compile time dependencies on it
        val proguard = project.configurations.create("proguard") {
            extendsFrom(project.configurations.getByName("runtimeOnly"))
        }

        proguard.dependencies.add(project.dependencies.create("com.guardsquare:proguard-base:7.6.1"))

        mainClass.set("proguard.ProGuard")
        classpath = project.configurations.getByName("proguard")

        val dir = javaLauncher.get().metadata.installationPath.dir("jmods")
        val jmodFiles = dir.asFile.listFiles { _, name -> name.endsWith(".jmod") }?.toList() ?: emptyList()

        jmodFiles.forEach {
            args("-libraryjars", "${it.absolutePath}(!**.jar;!module-info.class)")
        }

        args("-printseeds", "${getProguardOutputDir()}/seeds.txt")
        args("-printconfiguration", "${getProguardOutputDir()}/proguard.cfg")
        args("-dump", "${getProguardOutputDir()}/proguard.dump.txt")
        args("-whyareyoukeeping class io.specmatic.** { *; }")
//        args("-dontwarn")
        args("-dontoptimize")
        args("-keepattributes !LocalVariableTable, !LocalVariableTypeTable")
        // Keep all public members in the internal package
        args("-keep class !**.internal.** { public <fields>; public <methods>;}")
        // obfuscate everything in the internal package
        args("-keep,allowobfuscation class io.specmatic.**.internal.** { *; }")
        // Keep kotlin metadata
        args("-keep class kotlin.Metadata")
    }

    override fun exec() {
        super.exec()
        project.configurations.remove(project.configurations.getByName("proguard"))
    }

    @OutputFile
    fun getOutputJar(): File {
        return project.file("${project.layout.buildDirectory.get().asFile}/tmp/obfuscated-internal.jar")
    }

    @OutputDirectory
    fun getProguardOutputDir(): File {
        return File("${project.layout.buildDirectory.get().asFile}/proguard")
    }

}

private const val OBFUSCATE_JAR_INTERNAL = "obfuscateJarInternal"

class ObfuscateConfiguration(project: Project, projectConfiguration: ProjectConfiguration) {
    init {
        configureProguard(project, projectConfiguration.proguardExtraArgs)
    }

    private fun configureProguard(project: Project, proguardExtraArgs: List<String>?) {
        pluginDebug("Installing obfuscation hook on $project")
        project.pluginManager.withPlugin("java") {
            pluginDebug("Configuring obfuscation for on $project")
            val obfuscateJarInternalTask = project.tasks.register(OBFUSCATE_JAR_INTERNAL, ProguardTask::class.java) {

                val jarTask = project.tasks.named("jar", Jar::class.java).get()
                val jarTaskFile = jarTask.archiveFile
                dependsOn(jarTask)
                args("-libraryjars", project.configurations.getByName("compileClasspath").asPath)
                args("-injars", jarTaskFile.get().asFile)
                args("-outjars", getOutputJar())

                if (proguardExtraArgs != null) {
                    args(proguardExtraArgs)
                }
            }

            // Jar tasks automatically register as maven publication, so we "copy" the proguard jar into another one
            val obfuscateJarTask = project.tasks.register(OBFUSCATE_JAR_TASK, Jar::class.java) {
                group = "build"
                description = "Run obfuscation on the output of the `jar` task"

                dependsOn(obfuscateJarInternalTask)
                from(project.zipTree(obfuscateJarInternalTask.get().getOutputJar()))
                archiveClassifier.set("obfuscated")
            }
            obfuscateJarInternalTask.get().finalizedBy(obfuscateJarTask)

            project.tasks.getByName("assemble").dependsOn(obfuscateJarTask)
        }
    }
}
