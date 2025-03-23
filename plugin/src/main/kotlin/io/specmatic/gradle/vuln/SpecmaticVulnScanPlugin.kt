package io.specmatic.gradle.vuln

import org.cyclonedx.gradle.CycloneDxPlugin
import org.cyclonedx.gradle.CycloneDxTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

class SpecmaticVulnScanPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // for vuln scan, we need a SBOM first
        target.plugins.apply(CycloneDxPlugin::class.java)
        target.plugins.withType(CycloneDxPlugin::class.java) {
            target.tasks.withType(CycloneDxTask::class.java) {
                includeBomSerialNumber.set(false)
                // https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/271
                destination.set(project.layout.buildDirectory.get().asFile.resolve("reports/cyclonedx"))
            }

            // use a separate task to print, since the scan task is cacheable -- if the inputs/outputs don't change, the task won't run, and won't print the output
            val vulnScanTask = target.createJarVulnScanTask()

            target.allprojects {
                tasks.withType(Jar::class.java) {
                    finalizedBy(vulnScanTask)
                }
            }
        }
    }


}
