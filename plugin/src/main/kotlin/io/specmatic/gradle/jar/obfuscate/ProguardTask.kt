package io.specmatic.gradle.jar.obfuscate

import io.specmatic.gradle.exec.shellEscape
import io.specmatic.gradle.exec.shellEscapedArgs
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.ProjectInternal
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

    @get:Input
    val proguardVersion = "7.7.0"

    private var javaLauncher: Property<JavaLauncher> =
        objectFactory.property(JavaLauncher::class.java).convention(javaToolchainService.launcherFor {})

    @get:Input
    val proguardArgs = mutableListOf<String>()

    @get:InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    var inputJar: File? = null

    @get:OutputFile
    var outputJar: File? = null

    @TaskAction
    fun exec() {
        project.repositories.mavenCentral()

        val proguard =
            (project as ProjectInternal).configurations.resolvableDependencyScopeUnlocked("proguard_${name}") {
                isVisible = true
                isTransitive = true
//                isCanBeResolved = true
//                isCanBeConsumed = false
//                isCanBeDeclared = true
                // since proguard is GPL, we avoid compile time dependencies on it
                defaultDependencies {
                    add(project.dependencies.create("com.guardsquare:proguard-base:$proguardVersion"))
                }
            }

        val args = mutableListOf<String>()
        args.add("-cp")
        args.add(this.project.configurations.named("proguard_${this.name}").get().asPath)
        args.add("proguard.ProGuard") // main class
        args.addAll(createArgs())
        getArgsFile().writeText(args.joinToString("\n", transform = ::shellEscape))
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
            project.configurations.remove(proguard)
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
        return File("${project.layout.buildDirectory.get().asFile}/proguard-${name}")
    }

    private fun createArgs(): MutableList<String> {
        addLibraryArgs()

        appendProguardArgs("-injars", inputJar!!.absolutePath)
        appendProguardArgs("-outjars", outputJar!!.absolutePath)

        appendProguardArgs("-printseeds", "${getProguardOutputDir().resolve("seeds.txt")}")
        appendProguardArgs("-printconfiguration", "${getProguardOutputDir().resolve("proguard.cfg")}")
        appendProguardArgs("-dump", "${getProguardOutputDir().resolve("proguard.dump.txt")}")
        appendProguardArgs("-printmapping", "${getProguardOutputDir().resolve("proguard.mapping.txt")}")
        appendProguardArgs("-whyareyoukeeping", "class io.specmatic.** { *; }")
        appendProguardArgs("-dontoptimize")
        appendProguardArgs("-keepattributes", "!LocalVariableTable, !LocalVariableTypeTable")

        // Keep all public members in the internal package
        appendProguardArgs("-keep", "class !**.internal.** { public <fields>; public <methods>;}")
        // obfuscate everything in the internal package
        appendProguardArgs("-keep,allowobfuscation", "class io.specmatic.**.internal.** { *; }")
        // Keep kotlin metadata
        appendProguardArgs("-keep", "class kotlin.Metadata")
        return proguardArgs
    }

    private fun addLibraryArgs() {
        libraryJars(getRuntimeConfiguration())
        libraryJars(getJVMLibraryFiles().map { "${it.absolutePath}(!**.jar;!module-info.class)" })
    }

    fun appendProguardArgs(vararg toAdd: String?) {
        proguardArgs.addAll(toAdd.filterNotNull())
    }

    private fun libraryJars(configuration: Configuration) {
        libraryJars(configuration.files.map { it.absolutePath })
    }

    private fun libraryJars(libJar: Collection<String?>) {
        libJar.filterNotNull().forEach {
            appendProguardArgs("-libraryjars", it)
        }
    }

}
