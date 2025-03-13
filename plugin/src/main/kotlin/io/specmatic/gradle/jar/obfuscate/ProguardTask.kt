package io.specmatic.gradle.jar.obfuscate

import io.specmatic.gradle.exec.shellEscape
import io.specmatic.gradle.exec.shellEscapedArgs
import io.specmatic.gradle.pluginDebug
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.BaseExecSpec
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import java.io.File
import javax.inject.Inject

abstract class ProguardTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {

    @get:Nested
    abstract val launcher: Property<JavaLauncher>

    @get:Nested
    abstract val execLauncher: Property<ExecOperations>

    @get:Inject
    abstract val javaToolchainService: JavaToolchainService

    @get:Input
    private val args = mutableListOf<String>()

    @get:InputFile
    var inputJar: File? = null

    @get:OutputFile
    var outputJar: File? = null

    init {
        val toolchain = project.extensions.getByType(JavaPluginExtension::class.java).toolchain
        val defaultLauncher = javaToolchainService.launcherFor(toolchain)
        launcher.convention(defaultLauncher)
        execLauncher.set(execOperations)

        val proguard = project.configurations.create("proguard") {
            extendsFrom(project.configurations.getByName("runtimeOnly"))
        }

        proguard.dependencies.add(project.dependencies.create("com.guardsquare:proguard-base:7.6.1"))

        // add these args first!
        args("-cp")
        args(project.configurations.getByName("proguard").asPath)
        args("proguard.ProGuard") // main class
    }

    @TaskAction
    fun exec() {
        createArgs()

        val argsFile = project.file("$temporaryDir/proguard-task-args-${this.name}.txt")
        argsFile.writeText(args.joinToString("\n", transform = ::shellEscape))
        val completeCLI = listOf(getJavaExecutable().path, "@$argsFile")
        val outputFile = project.file("${getProguardOutputDir()}/proguard-task-output-${this.name}.log")
        val outputFileStream = outputFile.outputStream()
        pluginDebug("$ ${shellEscapedArgs(completeCLI)}")

        try {
            execOperations.exec {
                commandLine = completeCLI

                standardOutput = outputFileStream
                errorOutput = outputFileStream
            }
        } catch (e: ExecException) {
            pluginDebug(e.message!!)
            pluginDebug("Check the proguard output in $outputFile")
            throw e
        }

        project.configurations.remove(project.configurations.getByName("proguard"))
    }

    @InputFile
    fun getJavaExecutable(): File = launcher.get().executablePath.asFile

    @InputFiles
    fun getJVMLibraryFiles(): List<File> {
        val dir = launcher.get().metadata.installationPath.dir("jmods")
        return dir.asFile.listFiles { _, name -> name.endsWith(".jmod") }?.toList() ?: emptyList<File>()
    }

    @InputFiles
    fun getRuntimeConfiguration(): Configuration = this.project.configurations.getByName("runtimeClasspath")

    @OutputDirectory
    fun getProguardOutputDir(): File {
        return File("${project.layout.buildDirectory.get().asFile}/proguard")
    }

    private fun createArgs() {
        // since proguard is GPL, we avoid compile time dependencies on it


        addLibraryArgs()

        args("-injars", inputJar!!.absolutePath)
        args("-outjars", outputJar!!.absolutePath)

        args("-printseeds", "${getProguardOutputDir()}/seeds.txt")
        args("-printconfiguration", "${getProguardOutputDir()}/proguard.cfg")
        args("-dump", "${getProguardOutputDir()}/proguard.dump.txt")
        args("-whyareyoukeeping", "class io.specmatic.** { *; }")
        args("-dontoptimize")
        args("-keepattributes", "!LocalVariableTable, !LocalVariableTypeTable")

        // Keep all public members in the internal package
        args("-keep", "class !**.internal.** { public <fields>; public <methods>;}")
        // obfuscate everything in the internal package
        args("-keep,allowobfuscation", "class io.specmatic.**.internal.** { *; }")
        // Keep kotlin metadata
        args("-keep", "class kotlin.Metadata")

    }

    private fun addLibraryArgs() {
        libraryJars(getRuntimeConfiguration())
        libraryJars(getJVMLibraryFiles().map { "${it.absolutePath}(!**.jar;!module-info.class)" })
    }

    fun args(vararg toAdd: String?) {
        args.addAll(toAdd.filterNotNull())
    }

    private fun libraryJars(configuration: Configuration) {
        libraryJars(configuration.files.map { it.absolutePath })
    }

    private fun libraryJars(libJar: Collection<String?>) {
        libJar.filterNotNull().forEach {
            args("-libraryjars", it)
        }
    }

}
