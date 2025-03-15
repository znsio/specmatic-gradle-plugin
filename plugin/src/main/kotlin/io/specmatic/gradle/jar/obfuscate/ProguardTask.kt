package io.specmatic.gradle.jar.obfuscate

import io.specmatic.gradle.exec.shellEscape
import io.specmatic.gradle.exec.shellEscapedArgs
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class ProguardTask @Inject constructor(
    private val execLauncher: ExecOperations, javaToolchainService: JavaToolchainService, objectFactory: ObjectFactory
) : DefaultTask() {

    private var javaLauncher: Property<JavaLauncher> = objectFactory.property<JavaLauncher?>(JavaLauncher::class.java)
        .convention(javaToolchainService.launcherFor({}))

    init {
        println("ProguardTask initialized")
    }


    @get:Input
    private val argsInFile = mutableListOf<String>()

    @get:InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    var inputJar: File? = null

    @get:OutputFile
    var outputJar: File? = null

    init {
        val proguard = project.configurations.create("proguard")

        // since proguard is GPL, we avoid compile time dependencies on it
        proguard.dependencies.add(project.dependencies.create("com.guardsquare:proguard-base:7.6.1"))

        appendArgsToFile("-cp")
        appendArgsToFile(proguard.asPath)
        appendArgsToFile("proguard.ProGuard") // main class
    }

    @TaskAction
    fun exec() {

        createArgs()
        getArgsFile().writeText(argsInFile.joinToString("\n", transform = ::shellEscape))
        val completeCLI = listOf(javaLauncher.get().executablePath.asFile.path, "@${getArgsFile()}")
        val outputFile = project.file("${getProguardOutputDir()}/proguard-task-output-${this.name}.log")
        val outputFileStream = outputFile.outputStream()
        project.pluginInfo("$ ${shellEscapedArgs(completeCLI)}")

        try {
            execLauncher.exec {
                executable = javaLauncher.get().executablePath.asFile.path

                standardOutput = outputFileStream
                errorOutput = outputFileStream

                args("@${getArgsFile()}")
            }
        } catch (e: ExecException) {
            project.pluginInfo(e.message!!)
            project.pluginInfo("Check the proguard output in $outputFile")
            throw e
        } finally {
            project.configurations.remove(project.configurations.getByName("proguard"))
        }
    }

    private fun getArgsFile(): File {
        return getProguardOutputDir().resolve("args.txt")
    }

    private fun getJVMLibraryFiles(): List<File> {
        val dir = javaLauncher.get().metadata.installationPath.dir("jmods")
        return dir.asFile.listFiles { _, name -> name.endsWith(".jmod") }?.toList() ?: emptyList<File>()
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    fun getRuntimeConfiguration(): Configuration = this.project.configurations.getByName("runtimeClasspath")

    @OutputDirectory
    fun getProguardOutputDir(): File {
        return File("${project.layout.buildDirectory.get().asFile}/proguard")
    }

    private fun createArgs() {
        addLibraryArgs()

        appendArgsToFile("-injars", inputJar!!.absolutePath)
        appendArgsToFile("-outjars", outputJar!!.absolutePath)

        appendArgsToFile("-printseeds", "${getProguardOutputDir()}/seeds.txt")
        appendArgsToFile("-printconfiguration", "${getProguardOutputDir()}/proguard.cfg")
        appendArgsToFile("-dump", "${getProguardOutputDir()}/proguard.dump.txt")
        appendArgsToFile("-whyareyoukeeping", "class io.specmatic.** { *; }")
        appendArgsToFile("-dontoptimize")
        appendArgsToFile("-keepattributes", "!LocalVariableTable, !LocalVariableTypeTable")

        // Keep all public members in the internal package
        appendArgsToFile("-keep", "class !**.internal.** { public <fields>; public <methods>;}")
        // obfuscate everything in the internal package
        appendArgsToFile("-keep,allowobfuscation", "class io.specmatic.**.internal.** { *; }")
        // Keep kotlin metadata
        appendArgsToFile("-keep", "class kotlin.Metadata")
    }

    private fun addLibraryArgs() {
        libraryJars(getRuntimeConfiguration())
        libraryJars(getJVMLibraryFiles().map { "${it.absolutePath}(!**.jar;!module-info.class)" })
    }

    fun appendArgsToFile(vararg toAdd: String?) {
        argsInFile.addAll(toAdd.filterNotNull())
    }

    private fun libraryJars(configuration: Configuration) {
        libraryJars(configuration.files.map { it.absolutePath })
    }

    private fun libraryJars(libJar: Collection<String?>) {
        libJar.filterNotNull().forEach {
            appendArgsToFile("-libraryjars", it)
        }
    }

}
