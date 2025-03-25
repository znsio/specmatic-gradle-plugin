package io.specmatic.gradle.vuln

import io.specmatic.gradle.exec.shellEscapedArgs
import io.specmatic.gradle.license.pluginInfo
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils
import org.cyclonedx.gradle.CycloneDxTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject

@CacheableTask
abstract class AbstractVulnScanTask @Inject constructor(private val execLauncher: ExecOperations) : DefaultTask() {

    @TaskAction
    fun vulnScan() {
        maybeDownloadOsvScanner()

        getReportsDir().mkdirs()

        val formats = mapOf(
            "table" to getTextTableReportFile(), "json" to getJsonReportFile(), "html" to getHtmlReportFile()
        )

        val scanResults = formats.map { (format, output) -> runOsvScan(format, output) }

        if (scanResults.any { !it }) {
            throw GradleException("osv-scanner failed")
        }
    }

    @OutputDirectory
    abstract fun getReportsDir(): File

    @OutputFile
    fun getHtmlReportFile(): File = getReportsDir().resolve("report.html")

    @OutputFile
    fun getJsonReportFile(): File = getReportsDir().resolve("report.json")

    @OutputFile
    fun getTextTableReportFile(): File = getReportsDir().resolve("report.txt")

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    fun getOsvScannerPath(): File =
        project.gradle.gradleUserHomeDir.resolve("osv-scanner${if (getOS() == "windows") ".exe" else ""}")

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    fun getOsvScannerVersionPath(): File = project.gradle.gradleUserHomeDir.resolve("osv-scanner.version")

    private fun runOsvScan(format: String, output: File): Boolean {
        try {
            output.outputStream().use { outputStream: FileOutputStream ->
                val cliArgs = getCommandLine(format)
                project.pluginInfo("$ ${shellEscapedArgs(cliArgs)}")

                execLauncher.exec {
                    standardOutput = outputStream
                    errorOutput = System.err

                    commandLine = cliArgs
                }
            }
        } catch (e: Exception) {
            project.pluginInfo("osv-scanner failed with error: ${e.message}")
            return false
        }
        return true
    }

    abstract fun getCommandLine(format: String): List<String>

    private fun maybeDownloadOsvScanner() {
        val lastModified = if (getOsvScannerPath().exists()) getOsvScannerPath().lastModified() else 0L
        val oneWeekInMillis = 7 * 24 * 60 * 60 * 1000L
        val isOlderThanOneWeek = System.currentTimeMillis() - lastModified > oneWeekInMillis

        if (isOlderThanOneWeek) {
            project.pluginInfo("Checking if osv-scanner is up to date")
            val gitHub = GitHubBuilder().build()
            val repository = gitHub.getRepository("google/osv-scanner")
            val release = repository.latestRelease

            val currentVersion = if (getOsvScannerVersionPath().exists()) getOsvScannerVersionPath().readText() else ""
            if (currentVersion != release.name) {
                project.pluginInfo("osv-scanner is not up to date. Downloading version ${release.name} to ${getOsvScannerPath()}")
                val asset = release.listAssets().find {
                    it.name.contains(getOS()) && it.name.contains(getArch())
                } ?: throw RuntimeException("No asset found for osv-scanner for ${getOS()} ${getArch()}")

                val downloadUrl = asset.browserDownloadUrl
                FileUtils.copyURLToFile(URL(downloadUrl), getOsvScannerPath())
                getOsvScannerPath().setExecutable(true)
                getOsvScannerVersionPath().writeText(release.name)
            }
        }
    }

    @Input
    fun getOS(): String {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "windows"
        } else if (SystemUtils.IS_OS_MAC) {
            return "darwin"
        } else if (SystemUtils.IS_OS_LINUX) {
            return "linux"
        }
        throw RuntimeException("Unsupported operating system for osv-scanner: ${SystemUtils.OS_NAME}")
    }

    @Input
    fun getArch(): String {
        val osArch = SystemUtils.OS_ARCH
        return if (osArch.equals("x86_64", ignoreCase = true) || osArch.equals("amd64", ignoreCase = true)) {
            "amd64"
        } else {
            "arm64"
        }
    }
}

open class JarVulnScanTask @Inject constructor(execLauncher: ExecOperations) : AbstractVulnScanTask(execLauncher) {

    override fun getReportsDir(): File {
        return project.layout.buildDirectory.get().asFile.resolve("reports/osv-scan/source")
    }

    override fun getCommandLine(format: String): List<String> {
        return listOf(getOsvScannerPath().path, "scan", "source", "--format", format, getSbomFile().path)
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getSbomFile(): File = project.layout.buildDirectory.get().asFile.resolve("reports/cyclonedx/bom.json")
}

open class ImageVulnScanTask @Inject constructor(execLauncher: ExecOperations) : AbstractVulnScanTask(execLauncher) {
    @get:Input
    var image: String? = null

    override fun getReportsDir(): File {
        return project.layout.buildDirectory.get().asFile.resolve("reports/osv-scan/image")
    }

    override fun getCommandLine(format: String): List<String> {
        if (image == null) {
            throw GradleException("image property not set")
        }
        return listOf(getOsvScannerPath().path, "scan", "image", "--format", format, image!!)
    }

}

internal fun Project.createJarVulnScanTask(): TaskProvider<JarVulnScanTask> {
    val scanTaskName = "vulnScanJar"

    val printTask = project.tasks.register("${scanTaskName}Print") {
        group = "vulnerability"
        description = "Print vulnerabilities in $scanTaskName"

        doFirst {
            val textReportFile =
                project.tasks.named(scanTaskName, JarVulnScanTask::class.java).get().getTextTableReportFile()

            println(textReportFile.readText())
        }
    }

    return tasks.register(scanTaskName, JarVulnScanTask::class.java) {
        dependsOn(tasks.withType(CycloneDxTask::class.java))
        finalizedBy(printTask)
        group = "vulnerability"
        description = "Scan for vulnerabilities in jars"
    }
}

internal fun Project.createDockerVulnScanTask(imageName: String): TaskProvider<ImageVulnScanTask> {
    val scanTaskName = "vulnScanDocker"

    val printTask = project.tasks.register("${scanTaskName}Print") {
        group = "vulnerability"
        description = "Print vulnerabilities in $scanTaskName"

        doFirst {
            val textReportFile =
                project.tasks.named(scanTaskName, ImageVulnScanTask::class.java).get().getTextTableReportFile()

            println(textReportFile.readText())
        }
    }

    return tasks.register(scanTaskName, ImageVulnScanTask::class.java) {
        dependsOn(tasks.withType(CycloneDxTask::class.java))
        finalizedBy(printTask)
        group = "vulnerability"
        description = "Scan for vulnerabilities in jars"
        image = imageName
    }
}
