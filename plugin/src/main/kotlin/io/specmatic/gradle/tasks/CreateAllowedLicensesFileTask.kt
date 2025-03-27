package io.specmatic.gradle.tasks

import groovy.json.JsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File

private val ALLOWED_LICENSES = arrayOf(
    "0BSD",
    "Apache-2.0",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "Bouncy Castle Licence",
    "CC0-1.0",
    "CDDL-1.0",
    "CDDL-1.1",
    "EDL-1.0",
    "EPL-1.0",
    "EPL-2.0",
    "LGPL-2.1-only",
    "MIT",
    "MIT-0",
    "MPL-2.0",
    "Public-Domain",
    "Similar to Apache License but with the acknowledgment clause removed",
)

// Returns a file with the default allowed licenses. Structure is as follows:
// {
//     "allowedLicenses": [
//         {
//             "moduleLicense": "MIT",
//             "moduleName": ".*"
//         },
//         {
//             "moduleLicense": "Apache-2.0",
//             "moduleName": ".*"
//         },
//         ...
//     ]
// }
open class CreateAllowedLicensesFileTask : DefaultTask() {
    init {
        group = "verification"
        description = "Creates a file with the allowed licenses to be consumed by the gradle license plugin"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun get() {
        createDefaultAllowedLicensesFile(project)
    }
}

fun createDefaultAllowedLicensesFile(project: Project): File {
    val buildDir = project.layout.buildDirectory.get().asFile
    buildDir.mkdirs()
    val allowedLicensesFile = File(buildDir, "allowed-licenses.json")
    val allowedLicensesDocument = allowedLicenses(project).map({ eachLicense ->
        mapOf(
            "moduleLicense" to eachLicense, "moduleName" to ".*"
        )
    })

    allowedLicensesFile.writeText(
        JsonBuilder(mapOf("allowedLicenses" to allowedLicensesDocument)).toPrettyString()
    )
    return allowedLicensesFile
}

private fun allowedLicenses(project: Project): Array<String> {
    val extraAllowedLicenses = project.properties["extraAllowedLicenses"]
    return if (extraAllowedLicenses != null) {
        ALLOWED_LICENSES + extraAllowedLicenses.toString().split(",").map { it.trim() }
    } else {
        ALLOWED_LICENSES
    }
}
