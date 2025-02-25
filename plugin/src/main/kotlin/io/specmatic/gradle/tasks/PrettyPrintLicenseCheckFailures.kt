package io.specmatic.gradle.tasks

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

open class PrettyPrintLicenseCheckFailures : DefaultTask() {
    init {
        group = "verification"
        description = "Prints the license check failures in a pretty format"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun executeAction() {
        val checkLicenseTask = project.tasks.named("checkLicense")

        val depsWithoutAllowedLicensesReportFile = checkLicenseTask.get().outputs.files.files.first()

        if (!depsWithoutAllowedLicensesReportFile.exists()) {
            return
        }

        val json = JsonSlurper().parse(depsWithoutAllowedLicensesReportFile) as Map<*, *>

        val unsupportedLicenses = json["dependenciesWithoutAllowedLicenses"] as List<*>

        if (unsupportedLicenses.isNotEmpty()) {
            val unsupportedLicensesGroupedByLicense = unsupportedLicenses.groupBy { (it as Map<*, *>)["moduleLicense"] }
            val messages = mutableListOf<String>()

            unsupportedLicensesGroupedByLicense.forEach { (license, modules) ->
                messages.add("License: $license")
                (modules as List<Map<*, *>>).forEach { module ->
                    messages.add("   - ${module["moduleName"]} (${module["moduleVersion"]})")
                }
                messages.add("")
            }

            val joinedMessages = messages.joinToString("\n")
            throw GradleException("The following dependencies have licenses that are not allowed:\n\n$joinedMessages")
        }
    }

}