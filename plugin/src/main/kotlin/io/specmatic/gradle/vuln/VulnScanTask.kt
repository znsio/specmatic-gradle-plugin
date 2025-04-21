package io.specmatic.gradle.vuln

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.specmatic.gradle.exec.shellEscapedArgs
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.vuln.dto.VulnerabilityReport
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils
import org.cyclonedx.gradle.CycloneDxTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject

@CacheableTask
abstract class AbstractVulnScanTask @Inject constructor(private val execLauncher: ExecOperations) : DefaultTask() {
    @TaskAction
    fun vulnScan() {
        maybeDownloadTrivy()

        reportsDir.get().mkdirs()

        val formats = mapOf(
            "table" to getTextTableReportFile(),
            "json" to getJsonReportFile(),
        )

        formats.map { (format, output) -> runScan(format, output) }
    }

    @get:OutputDirectory
    abstract val reportsDir: Property<File>

    @OutputFile
    fun getJsonReportFile(): File = reportsDir.get().resolve("report.json")

    @OutputFile
    fun getTextTableReportFile(): File = reportsDir.get().resolve("report.txt")

    @get:OutputDirectory
    abstract val trivyHomeDir: Property<File>

    private fun runScan(format: String, output: File): Boolean {
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
            project.pluginInfo("trivy failed with error: ${e.message} (ignoring error)")
            return false
        }
        return true
    }

    abstract fun getCommandLine(format: String): List<String>

    private fun maybeDownloadTrivy() {
        trivyHomeDir.get().mkdirs()

        // acquire a file lock to prevent multiple tasks from trying to download trivy at the same time
        val lockFile = trivyHomeDir.get().resolve("trivy-download.lock")
        RandomAccessFile(lockFile, "rw").channel.use { channel ->
            channel.lock().use {
                val lastModified = if (trivyInstallDir().exists()) trivyInstallDir().lastModified() else 0L
                val oneWeekInMillis = 7 * 24 * 60 * 60 * 1000L
                val isOlderThanOneWeek = System.currentTimeMillis() - lastModified > oneWeekInMillis
                if (isOlderThanOneWeek) {
                    downloadTrivy()
                }
            }
        }
    }

    private fun trivyInstallDir(): File = trivyHomeDir.get().resolve("trivy")
    private fun trivyVersionFile(): File = trivyHomeDir.get().resolve("trivy.version")

    private fun downloadTrivy() {
        project.pluginInfo("Checking if trivy is up to date")
        val gitHub = GitHubBuilder().build()
        val repository = gitHub.getRepository("aquasecurity/trivy")
        val release = repository.latestRelease

        val currentVersion = if (trivyVersionFile().exists()) trivyVersionFile().readText() else "unknown"

        if (currentVersion != release.name) {
            val asset = release.listAssets().find {
                it.name.lowercase().contains("_${os}-${arch}") && (it.name.lowercase()
                    .endsWith(".zip") || it.name.lowercase().endsWith(".tar.gz"))
            } ?: throw RuntimeException("No asset found for trivy for $os $arch")
            val trivyCompressedDownloadPath = temporaryDir.resolve(asset.name)
            val downloadUrl = asset.browserDownloadUrl

            project.pluginInfo("Currently installed trivy version($currentVersion) is not up-to-date. Downloading version ${release.name} from $downloadUrl to $trivyCompressedDownloadPath")
            FileUtils.copyURLToFile(URL(downloadUrl), trivyCompressedDownloadPath)
            project.delete(trivyInstallDir())
            trivyInstallDir().mkdirs()
            project.copy {
                if (trivyCompressedDownloadPath.endsWith(".zip")) {
                    from(project.zipTree(trivyCompressedDownloadPath))
                } else {
                    from(project.tarTree(trivyCompressedDownloadPath))
                }
                into(trivyInstallDir())
            }

            trivyVersionFile().writeText(release.name)
        }
    }

    @get:Input
    val os: String
        get() = when {
            SystemUtils.IS_OS_WINDOWS -> "windows"
            SystemUtils.IS_OS_MAC -> "macos"
            SystemUtils.IS_OS_LINUX -> "linux"
            else -> throw RuntimeException("Unsupported operating system for trivy: ${SystemUtils.OS_NAME}")
        }

    @get:Input
    val arch: String
        get() {
            val osArch = SystemUtils.OS_ARCH.lowercase()

            return when {
                (osArch.contains("x86") || osArch.contains("amd64")) && osArch.contains("64") -> "64bit"
                osArch.contains("aarch") && osArch.contains("64") -> "arm64"
                else -> throw GradleException("Unsupported architecture for trivy: $osArch")
            }
        }

    protected fun trivyExecutable(): String {
        val extension = if (os == "windows") ".exe" else ""

        return trivyInstallDir().resolve("trivy$extension").path
    }

    @get:Input
    protected val commonArgs: Array<String>
        get() = arrayOf(
            "--quiet",
            "--no-progress",
        )
}

abstract class SBOMVulnScanTask @Inject constructor(execLauncher: ExecOperations) : AbstractVulnScanTask(execLauncher) {

    override fun getCommandLine(format: String): List<String> {
        return listOf(
            trivyExecutable(), "sbom", "--format", format, *commonArgs, sbomFile.get().path
        )
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sbomFile: Property<File>
}

abstract class ImageVulnScanTask @Inject constructor(execLauncher: ExecOperations) :
    AbstractVulnScanTask(execLauncher) {
    @get:Input
    var image: String? = null

    override fun getCommandLine(format: String): List<String> {
        if (image == null) {
            throw GradleException("image property not set")
        }
        return listOf(trivyExecutable(), "image", "--format", format, *commonArgs, image!!)
    }
}

internal fun Project.createJarVulnScanTask(): TaskProvider<SBOMVulnScanTask> {
    val scanTaskName = "vulnScanJar"

    val thisProject = this
    val printTask = project.tasks.register("${scanTaskName}Print") {
        group = "vulnerability"
        description = "Print vulnerabilities in $scanTaskName"

        doFirst {
            printReportFile(
                thisProject,
                thisProject.tasks.named(scanTaskName, SBOMVulnScanTask::class.java).get().getTextTableReportFile()
            )
        }
    }

    return tasks.register(scanTaskName, SBOMVulnScanTask::class.java) {
        dependsOn(tasks.withType(CycloneDxTask::class.java))
        finalizedBy(printTask)
        group = "vulnerability"
        description = "Scan for vulnerabilities in jars"

        trivyHomeDir.set(trivyHomeDir())
        sbomFile.set(project.layout.buildDirectory.get().asFile.resolve("reports/cyclonedx/bom.json"))

        reportsDir.set(project.layout.buildDirectory.get().asFile.resolve("reports/vulnerabilities/source"))
    }
}

private fun trivyHomeDir(): File = SystemUtils.getUserHome().resolve(".specmatic-trivy")

internal fun Project.createDockerVulnScanTask(imageName: String): TaskProvider<ImageVulnScanTask> {
    val scanTaskName = "vulnScanDocker"

    val rootProject = this.rootProject

    val printTask = rootProject.tasks.register("${scanTaskName}Print") {
        group = "vulnerability"
        description = "Print vulnerabilities in $scanTaskName"

        doFirst {
            val reportFile =
                rootProject.tasks.named(scanTaskName, ImageVulnScanTask::class.java).get().getTextTableReportFile()
            printReportFile(rootProject, reportFile)
        }
    }

    return rootProject.tasks.register(scanTaskName, ImageVulnScanTask::class.java) {
        dependsOn(rootProject.tasks.withType(CycloneDxTask::class.java))
        finalizedBy(printTask)
        group = "vulnerability"
        description = "Scan for vulnerabilities in jars"
        image = imageName

        trivyHomeDir.set(trivyHomeDir())
        reportsDir.set(project.layout.buildDirectory.get().asFile.resolve("reports/vulnerabilities/image"))
    }
}

data class TableRow(
    val type: String,
    val packageName: String,
    val maxSeverity: String,
    val version: String,
    val vulnerabilityURL: String,
    val modified: Date,
    val published: Date,
    val summary: String?,
) {
    val formattedModified: String = formatDate(modified)
    val formattedSeverity: String = when (maxSeverity.lowercase()) {
        "unknown" -> "(unknown)"
        "low" -> "$maxSeverity 🟢"
        "medium" -> "$maxSeverity 🟡"
        "high" -> "$maxSeverity 🟠"
        "critical" -> "$maxSeverity 🔴"
        else -> "(invalid severity)"

    }
    val formattedPublished: String = formatDate(published)
    val formattedSummary: String = summary?.replace("\n", " ") ?: ""
    val formattedPackageName: String = packageName
    val formattedVersion: String = version

    private fun formatDate(date: Date): String {
        val daysAgo = ChronoUnit.DAYS.between(date.toInstant(), Instant.now())
        return "${SimpleDateFormat("yyyy-MM-dd").format(date)} ($daysAgo days ago)"
    }

    fun dateColor(date: Date): StyledTextOutput.Style {
        val modifiedColor = when (ChronoUnit.DAYS.between(date.toInstant(), Instant.now())) {
            in 0..14 -> StyledTextOutput.Style.UserInput // Whilte
            in 15..30 -> StyledTextOutput.Style.ProgressStatus // Amber
            else -> StyledTextOutput.Style.Failure // Red
        }
        return modifiedColor
    }

    val severityColor: StyledTextOutput.Style = when (maxSeverity.lowercase()) {
        "unknown", "low" -> StyledTextOutput.Style.UserInput // White
        "medium" -> StyledTextOutput.Style.ProgressStatus // Amber
        "high", "critical" -> StyledTextOutput.Style.Failure // Red
        else -> StyledTextOutput.Style.Failure // Red
    }
}

private fun printReportFile(project: Project, reportFile: File) {
    project.logger.warn(reportFile.readText())
    project.logger.warn("Vulnerability report file: ${reportFile.toURI()}")
}

private fun printReportFileJSON(project: Project, reportFile: File) {

    val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true)

    val report: VulnerabilityReport = mapper.readValue(
        reportFile, VulnerabilityReport::class.java
    )

    val tableRows = report.results.flatMap { result ->
        result.vulnerabilities.map { eachVulnerability ->
            TableRow(
                type = "${result.target} (${result.type})",
                packageName = eachVulnerability.pkgName,
                maxSeverity = eachVulnerability.severity,
                version = eachVulnerability.installedVersion,
                vulnerabilityURL = eachVulnerability.primaryUrl,
                modified = Date.from(Instant.parse(eachVulnerability.lastModifiedDate)),
                published = Date.from(Instant.parse(eachVulnerability.publishedDate)),
                summary = eachVulnerability.title
            )
        }
    }


    val columnWidths = mutableMapOf(
        "Package" to "Package".length,
        "Max Severity" to "Max Severity".length,
        "Version" to "Version".length,
        "Fix Version" to "Fix Version".length,
        "Vulnerability" to "Vulnerability".length,
        "Modified" to "Modified".length,
        "Published" to "Published".length,
        "Summary" to "Summary".length
    )

    tableRows.forEach { row ->
        columnWidths["Package"] = maxOf(columnWidths["Package"]!!, row.formattedPackageName.length)
        columnWidths["Max Severity"] = maxOf(columnWidths["Max Severity"]!!, row.formattedSeverity.length)
        columnWidths["Version"] = maxOf(columnWidths["Version"]!!, row.formattedVersion.length)
        columnWidths["Vulnerability"] = maxOf(columnWidths["Vulnerability"]!!, row.vulnerabilityURL.length)
        columnWidths["Modified"] = maxOf(columnWidths["Modified"]!!, row.formattedModified.length)
        columnWidths["Published"] = maxOf(columnWidths["Published"]!!, row.formattedPublished.length)
        columnWidths["Summary"] = maxOf(columnWidths["Summary"]!!, row.formattedSummary.length)
    }

    val output: StyledTextOutput = project.serviceOf<StyledTextOutputFactory>().create("vulnScan")

    output.style(StyledTextOutput.Style.Header).text(
        String.format(
            "╔%s╦%s╦%s╦%s╦%s╦%s╦%s╗\n",
            "═".repeat(2 + columnWidths["Package"]!!),
            "═".repeat(2 + columnWidths["Max Severity"]!!),
            "═".repeat(2 + columnWidths["Version"]!!),
            "═".repeat(2 + columnWidths["Vulnerability"]!!),
            "═".repeat(2 + columnWidths["Modified"]!!),
            "═".repeat(2 + columnWidths["Published"]!!),
            "═".repeat(2 + columnWidths["Summary"]!!)
        )
    )

    output.style(StyledTextOutput.Style.Header).text(
        String.format(
            "║ %-${columnWidths["Package"]}s ║ %-${columnWidths["Max Severity"]}s ║ %-${columnWidths["Version"]}s ║ %-${columnWidths["Vulnerability"]}s ║ %-${columnWidths["Modified"]}s ║ %-${columnWidths["Published"]}s ║ %-${columnWidths["Summary"]}s ║\n",
            "Package",
            "Max Severity",
            "Version",
            "Vulnerability",
            "Modified",
            "Published",
            "Summary"
        )
    )

    output.style(StyledTextOutput.Style.Header).text(
        String.format(
            "╠%s╬%s╬%s╬%s╬%s╬%s╬%s╣\n",
            "═".repeat(2 + columnWidths["Package"]!!),
            "═".repeat(2 + columnWidths["Max Severity"]!!),
            "═".repeat(2 + columnWidths["Version"]!!),
            "═".repeat(2 + columnWidths["Vulnerability"]!!),
            "═".repeat(2 + columnWidths["Modified"]!!),
            "═".repeat(2 + columnWidths["Published"]!!),
            "═".repeat(2 + columnWidths["Summary"]!!)
        )
    )

    tableRows.forEach { row ->
        output.style(StyledTextOutput.Style.Normal).text("║ ")
        output.style(StyledTextOutput.Style.Normal).text(row.formattedPackageName.padEnd(columnWidths["Package"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(row.severityColor).text(row.formattedSeverity.padEnd(columnWidths["Max Severity"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(StyledTextOutput.Style.Normal).text(row.formattedVersion.padEnd(columnWidths["Version"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(StyledTextOutput.Style.Normal).text(row.vulnerabilityURL.padEnd(columnWidths["Vulnerability"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(row.dateColor(row.modified)).text(row.formattedModified.padEnd(columnWidths["Modified"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(row.dateColor(row.published)).text(row.formattedPublished.padEnd(columnWidths["Published"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(StyledTextOutput.Style.Normal).text(row.formattedSummary.padEnd(columnWidths["Summary"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║\n")
    }

    output.style(StyledTextOutput.Style.Header).text(
        String.format(
            "╚%s╩%s╩%s╩%s╩%s╩%s╩%s╝\n",
            "═".repeat(2 + columnWidths["Package"]!!),
            "═".repeat(2 + columnWidths["Max Severity"]!!),
            "═".repeat(2 + columnWidths["Version"]!!),
            "═".repeat(2 + columnWidths["Vulnerability"]!!),
            "═".repeat(2 + columnWidths["Modified"]!!),
            "═".repeat(2 + columnWidths["Published"]!!),
            "═".repeat(2 + columnWidths["Summary"]!!)
        )
    )
}
