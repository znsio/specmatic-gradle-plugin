package io.specmatic.gradle.license

import com.github.jk1.license.ImportedModuleBundle
import com.github.jk1.license.ImportedModuleData
import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.LicenseReportPlugin
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.filter.SpdxLicenseBundleNormalizer
import com.github.jk1.license.importer.DependencyDataImporter
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import io.specmatic.gradle.SpecmaticGradlePlugin
import io.specmatic.gradle.extensions.ModuleLicenseData
import io.specmatic.gradle.specmaticExtension
import io.specmatic.gradle.tasks.CreateAllowedLicensesFileTask
import io.specmatic.gradle.tasks.PrettyPrintLicenseCheckFailures
import io.specmatic.gradle.tasks.createDefaultAllowedLicensesFile
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar


internal class CustomLicenseImporter(private val allowedLicenses: MutableList<ModuleLicenseData>) :
    DependencyDataImporter {
    override fun getImporterName(): String {
        return "SpecmaticCustomImporter"
    }

    override fun doImport(): Collection<ImportedModuleBundle> {
        return listOf(ImportedModuleBundle(null, allowedLicenses.map {
            ImportedModuleData(
                it.name, it.version, it.projectUrl, it.license, it.licenseUrl
            )
        }))
    }
}

class SpecmaticLicenseReportingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginInfo("Configuring license reporting")
        project.plugins.apply(LicenseReportPlugin::class.java)
        project.plugins.withType(LicenseReportPlugin::class.java) {
            val prettyPrintLicenseCheckFailuresTask = createPrettyPrintLicenseCheckFailuresTask(project)
            val createAllowedLicensesFileTask = createCreateAllowedLicensesFileTask(project)

            // because we applied LicenseReportPlugin, we have to install a callback to configure the rest of the stuff
            project.afterEvaluate {
                val specmaticExtension = project.specmaticExtension()
                val reportExtension = licenseReportExtension(project)

                val buildDir = project.layout.buildDirectory.get().asFile
                if (!buildDir.exists()) {
                    buildDir.mkdirs()
                }

                reportExtension.filters = filters()
                reportExtension.importers = arrayOf(CustomLicenseImporter(specmaticExtension.licenseData))
                reportExtension.renderers = renderers()
                reportExtension.allowedLicensesFile = createDefaultAllowedLicensesFile(project)
                reportExtension.excludeGroups = arrayOf("io.specmatic.*")

                configureTaskDependencies(project, prettyPrintLicenseCheckFailuresTask, createAllowedLicensesFileTask)
            }
        }
    }


    companion object {
        private fun renderers(): Array<ReportRenderer> {
            val simpleHtmlReportRenderer = SimpleHtmlReportRenderer("index.html")
            val inventoryHtmlReportRenderer = InventoryHtmlReportRenderer("inventory.html")
            return arrayOf(simpleHtmlReportRenderer, inventoryHtmlReportRenderer)
        }

        private fun filters(): Array<LicenseBundleNormalizer> {
            val licenseBundleNormalizer = LicenseBundleNormalizer(
                SpecmaticGradlePlugin::class.java.classLoader.getResourceAsStream("license-normalization.json"), true
            )
            val spdxLicenseBundleNormalizer = SpdxLicenseBundleNormalizer()

            return arrayOf(
                licenseBundleNormalizer, spdxLicenseBundleNormalizer
            )
        }

        private fun configureTaskDependencies(
            project: Project,
            prettyPrintLicenseCheckFailuresTask: TaskProvider<PrettyPrintLicenseCheckFailures>,
            createAllowedLicensesFileTask: TaskProvider<CreateAllowedLicensesFileTask>
        ) {

            project.allprojects {
                // Configure all Jar tasks in subprojects to be finalized by the checkLicense task
                tasks.withType(Jar::class.java) {
                    finalizedBy(project.tasks.named("checkLicense"))
                }
            }

            project.tasks.named("checkLicense") {
                // always regenerate. because caching on this task is broken
                outputs.upToDateWhen { false }

                dependsOn(createAllowedLicensesFileTask)
                finalizedBy(prettyPrintLicenseCheckFailuresTask)
            }

            project.tasks.named("generateLicenseReport") {
                // always regenerate. because caching on this task is broken
                outputs.upToDateWhen { false }
            }
        }

        private fun licenseReportExtension(project: Project) =
            project.extensions.findByType(LicenseReportExtension::class.java) ?: throw GradleException(
                "License report extension not found"
            )

        private fun createPrettyPrintLicenseCheckFailuresTask(project: Project): TaskProvider<PrettyPrintLicenseCheckFailures> =
            project.tasks.register("prettyPrintLicenseCheckFailures", PrettyPrintLicenseCheckFailures::class.java)


        private fun createCreateAllowedLicensesFileTask(project: Project): TaskProvider<CreateAllowedLicensesFileTask> =
            project.tasks.register("createAllowedLicensesFile", CreateAllowedLicensesFileTask::class.java)

    }
}

fun Project.pluginInfo(string: String) {
    println("[SGP - ${this.path}]: $string")
}
