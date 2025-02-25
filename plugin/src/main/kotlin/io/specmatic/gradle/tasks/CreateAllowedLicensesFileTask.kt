package io.specmatic.gradle.tasks

import groovy.json.JsonBuilder
import io.specmatic.gradle.ALLOWED_LICENSES
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File

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
    val allowedLicensesFile = File(project.layout.buildDirectory.get().asFile, "allowed-licenses.json")
    val allowedLicensesDocument = ALLOWED_LICENSES.map({ eachLicense ->
        mapOf(
            "moduleLicense" to eachLicense, "moduleName" to ".*"
        )
    })

    allowedLicensesFile.writeText(
        JsonBuilder(mapOf("allowedLicenses" to allowedLicensesDocument)).toPrettyString()
    )
    return allowedLicensesFile
}