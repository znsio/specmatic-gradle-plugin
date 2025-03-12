package io.specmatic.gradle.obfuscate

import io.specmatic.gradle.exec.shellEscape
import io.specmatic.gradle.extensions.ProjectConfiguration
import io.specmatic.gradle.pluginDebug
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.File
import javax.inject.Inject

const val OBFUSCATE_JAR_TASK = "obfuscateJar"

abstract class ProguardTask : Exec() {
    @get:Inject
    protected abstract val javaToolchainService: JavaToolchainService

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>


    init {
        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
        val defaultLauncher = javaToolchainService.launcherFor(toolchain)
        javaLauncher.convention(defaultLauncher)
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


    fun createArgs(): MutableList<String> {

        // since proguard is GPL, we avoid compile time dependencies on it
        val proguard = project.configurations.create("proguard") {
            extendsFrom(project.configurations.getByName("runtimeOnly"))
        }

        proguard.dependencies.add(project.dependencies.create("com.guardsquare:proguard-base:7.6.1"))


        val dir = javaLauncher.get().metadata.installationPath.dir("jmods")
        val jmodFiles = dir.asFile.listFiles { _, name -> name.endsWith(".jmod") }?.toList() ?: emptyList()

        val cliArgs = mutableListOf<String>()
        executable(javaLauncher.get().executablePath.asFile.path)
        cliArgs.add("-cp")
        cliArgs.add(project.configurations.getByName("proguard").asPath)
        cliArgs.add("proguard.ProGuard") // main class
        jmodFiles.forEach {
            cliArgs.add("-libraryjars")
            cliArgs.add("${it.absolutePath}(!**.jar;!module-info.class)")
        }

        cliArgs.add("-printseeds")
        cliArgs.add("${getProguardOutputDir()}/seeds.txt")
        cliArgs.add("-printconfiguration")
        cliArgs.add("${getProguardOutputDir()}/proguard.cfg")
        cliArgs.add("-dump")
        cliArgs.add("${getProguardOutputDir()}/proguard.dump.txt")
        cliArgs.add("-whyareyoukeeping class io.specmatic.** { *; }")
//        cliArgs.add("-dontwarn")
        cliArgs.add("-dontoptimize")
        cliArgs.add("-keepattributes !LocalVariableTable, !LocalVariableTypeTable")
        // Keep all public members in the internal package
        cliArgs.add("-keep class !**.internal.** { public <fields>; public <methods>;}")
        // obfuscate everything in the internal package
        cliArgs.add("-keep,allowobfuscation class io.specmatic.**.internal.** { *; }")
        // Keep kotlin metadata
        cliArgs.add("-keep class kotlin.Metadata")
        return cliArgs
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

                val runtimeClasspath = project.configurations.getByName("runtimeClasspath")
                val cliArgs = createArgs()
                runtimeClasspath.files.forEach {
                    cliArgs.add("-libraryjars")
                    cliArgs.add(it.absolutePath)
                }
                cliArgs.add("-injars")
                cliArgs.add(jarTaskFile.get().asFile.path)
                cliArgs.add("-outjars")
                cliArgs.add(getOutputJar().path)
                cliArgs.addAll(proguardExtraArgs.orEmpty())

                val argsFile = project.file("${temporaryDir}/java-args")
                argsFile.writeText(cliArgs.joinToString("\n", transform = ::shellEscape))
                args("@$argsFile")
                inputs.property("proguardArgs", cliArgs)

                inputs.files(jarTaskFile.get().asFile)
                inputs.files(runtimeClasspath)

                outputs.file(getOutputJar())
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
