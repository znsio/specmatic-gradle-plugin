package io.specmatic.gradle.vuln

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.specmatic.gradle.exec.shellEscapedArgs
import io.specmatic.gradle.license.pluginInfo
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils
import org.cyclonedx.gradle.CycloneDxTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.io.FileOutputStream
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
        maybeDownloadOsvScanner()

        getReportsDir().mkdirs()

        val formats = mapOf(
            "table" to getTextTableReportFile(), "json" to getJsonReportFile(), "html" to getHtmlReportFile()
        )

        formats.map { (format, output) -> runOsvScan(format, output) }

//        if (scanResults.any { !it }) {
//            throw GradleException("osv-scanner failed")
//        }
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
            printReportFile(
                project, project.tasks.named(scanTaskName, JarVulnScanTask::class.java).get().getJsonReportFile()
            )
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

    val printTask = rootProject.tasks.register("${scanTaskName}Print") {
        group = "vulnerability"
        description = "Print vulnerabilities in $scanTaskName"

        doFirst {
            val reportFile =
                rootProject.tasks.named(scanTaskName, ImageVulnScanTask::class.java).get().getJsonReportFile()
            printReportFile(rootProject, reportFile)
        }
    }

    return rootProject.tasks.register(scanTaskName, ImageVulnScanTask::class.java) {
        dependsOn(rootProject.tasks.withType(CycloneDxTask::class.java))
        finalizedBy(printTask)
        group = "vulnerability"
        description = "Scan for vulnerabilities in jars"
        image = imageName
    }
}


data class TableRow(
    val packageName: String,
    val maxSeverity: String,
    val version: String,
    val vulnerability: String,
    val modified: Date,
    val published: Date,
    val summary: String?,
) {
    val formattedModified: String = formatDate(modified)
    val formattedSeverity: String =
        if (maxSeverity.isBlank()) {
            "(unknown)"
        } else if (maxSeverity.toFloat() in 0.0..3.9) "$maxSeverity (low) 🟢"
        else if (maxSeverity.toFloat() in 4.0..6.9) "$maxSeverity (medium) 🟡"
        else if (maxSeverity.toFloat() in 7.0..8.9) "$maxSeverity (high) 🟠"
        else "$maxSeverity (critical) 🔴"
    val formattedPublished: String = formatDate(published)
    val formattedSummary: String = summary?.replace("\n", " ") ?: ""
    val formattedVulnerability: String = "https://osv.dev/${vulnerability}"
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

    val severityColor: StyledTextOutput.Style =
        if (maxSeverity.isBlank()) StyledTextOutput.Style.UserInput// Whilte
        else if (maxSeverity.toFloat() in 0.0..3.9) StyledTextOutput.Style.UserInput// Whilte
        else if (maxSeverity.toFloat() in 4.0..6.9) StyledTextOutput.Style.ProgressStatus// Amber
        else if (maxSeverity.toFloat() in 7.0..8.9) StyledTextOutput.Style.Failure // Red
        else StyledTextOutput.Style.Failure //Red

}

private fun printReportFile(project: Project, reportFile: File) {
    val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true)
    val report: Report = mapper.readValue(
        reportFile, Report::class.java
    )


    val tableRows = report.results.flatMap { result ->
        result.packages.flatMap { pkg ->
            pkg.vulnerabilities.flatMap { vulnerability ->

                vulnerability.affected.flatMap { affected ->
                    affected.ranges.flatMap { range ->
                        range.events.map { event ->
                            val maxSeverity =
                                pkg.groups.maxBy { if (it.maxSeverity.isNullOrBlank()) 0.0.toFloat() else it.maxSeverity.toFloat() }.maxSeverity
                                    ?: "0.0"


                            TableRow(
                                packageName = pkg.packageInfo.name,
                                maxSeverity = maxSeverity,
                                version = pkg.packageInfo.version,
                                vulnerability = vulnerability.id,
                                modified = vulnerability.modified,
                                published = vulnerability.published,
                                summary = vulnerability.summary
                            )

                        }
                    }
                }

            }
        }
    }


    val uniqueTableRows = tableRows.distinctBy { it.packageName + it.version + it.vulnerability }
    if (uniqueTableRows.isEmpty()) {
        return
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

    uniqueTableRows.forEach { row ->
        columnWidths["Package"] = maxOf(columnWidths["Package"]!!, row.formattedPackageName.length)
        columnWidths["Max Severity"] = maxOf(columnWidths["Max Severity"]!!, row.formattedSeverity.length)
        columnWidths["Version"] = maxOf(columnWidths["Version"]!!, row.formattedVersion.length)
        columnWidths["Vulnerability"] = maxOf(columnWidths["Vulnerability"]!!, row.formattedVulnerability.length)
        columnWidths["Modified"] = maxOf(columnWidths["Modified"]!!, row.formattedModified.length)
        columnWidths["Published"] = maxOf(columnWidths["Published"]!!, row.formattedPublished.length)
        columnWidths["Summary"] = maxOf(columnWidths["Summary"]!!, row.formattedSummary.length)
    }

    val output: StyledTextOutput = project.serviceOf<StyledTextOutputFactory>().create("vulnScan")
//    StyledTextOutput.Style.values().forEach {
//        output.style(it).text("hello world - ${it.name}\n").style(StyledTextOutput.Style.Normal)
//    }

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

    uniqueTableRows.forEach { row ->
        output.style(StyledTextOutput.Style.Normal).text("║ ")
        output.style(StyledTextOutput.Style.Normal).text(row.formattedPackageName.padEnd(columnWidths["Package"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(row.severityColor).text(row.formattedSeverity.padEnd(columnWidths["Max Severity"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(StyledTextOutput.Style.Normal).text(row.formattedVersion.padEnd(columnWidths["Version"]!!))

        output.style(StyledTextOutput.Style.Normal).text(" ║ ")
        output.style(StyledTextOutput.Style.Normal)
            .text(row.formattedVulnerability.padEnd(columnWidths["Vulnerability"]!!))

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


//    if (uniqueTableRows.isNotEmpty()) {
//        throw GradleException("Vulnerabilities found in the scan")
//    }
}


private data class Report(
    @field:JsonProperty("results") val results: List<Result>,
)

private data class Result(
    @field:JsonProperty("source") val source: Source,
    @field:JsonProperty("packages") val packages: List<Package>
)

private data class Source(
    @field:JsonProperty("path") val path: String, @field:JsonProperty("type") val type: String
)

private data class Package(
    @field:JsonProperty("package") val packageInfo: PackageInfo,
    @field:JsonProperty("vulnerabilities") val vulnerabilities: List<Vulnerability>,
    @field:JsonProperty("groups") val groups: List<Group>
)

private data class PackageInfo(
    @field:JsonProperty("name") val name: String,
    @field:JsonProperty("version") val version: String,
    @field:JsonProperty("ecosystem") val ecosystem: String
)

private data class Vulnerability(
    @field:JsonProperty("modified") val modified: Date,
    @field:JsonProperty("published") val published: Date,
    @field:JsonProperty("id") val id: String,
    @field:JsonProperty("summary") val summary: String? = "[none]",
    @field:JsonProperty("severity") val severity: List<Severity>?,
    @field:JsonProperty("affected") val affected: List<Affected>,
)

private data class Severity(
    @field:JsonProperty("type") val type: String,
    @field:JsonProperty("score") val score: String,
)

private data class Affected(
    @field:JsonProperty("package") val packageInfo: AffectedPackageInfo?,
    @field:JsonProperty("ranges") val ranges: List<Range>,
    @field:JsonProperty("versions") val versions: List<String>,
)

private data class AffectedPackageInfo(
    @field:JsonProperty("name") val name: String,
    @field:JsonProperty("ecosystem") val ecosystem: String,
    @field:JsonProperty("purl") val purl: String
)

private data class Range(
    @field:JsonProperty("type") val type: String,
    @field:JsonProperty("events") val events: List<Event>,
)

private data class Event(
    @field:JsonProperty("introduced") val introduced: String?,
    @field:JsonProperty("fixed") val fixed: String?,
    @field:JsonProperty("last_affected") val lastAffected: String?
)

private data class Group(
    @field:JsonProperty("ids") val ids: List<String>,
    @field:JsonProperty("aliases") val aliases: List<String>,
    @field:JsonProperty("max_severity") val maxSeverity: String?
)
