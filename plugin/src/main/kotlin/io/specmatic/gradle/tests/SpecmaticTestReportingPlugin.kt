package io.specmatic.gradle.tests

import com.adarshr.gradle.testlogger.TestLoggerPlugin
import io.specmatic.gradle.license.pluginInfo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport

internal class SpecmaticTestReportingPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginInfo("$target - wire test logger, jacoco, and junit")
        target.plugins.apply(TestLoggerPlugin::class.java)
        target.plugins.apply(JacocoPlugin::class.java)

        target.plugins.withType(JavaPlugin::class.java) {
            target.plugins.withType(JacocoPlugin::class.java) {
                configureJunit(target)
                configureJacoco(target)
            }
        }
    }

    private fun configureJunit(eachProject: Project) {
        eachProject.tasks.withType(Test::class.java) {
            eachProject.pluginInfo("Configuring junitPlatform on ${this.path}")
            useJUnitPlatform()
            defaultCharacterEncoding = "UTF-8"
            filter.isFailOnNoMatchingTests = true
        }
    }

    private fun configureJacoco(eachProject: Project) {
        eachProject.tasks.withType(Test::class.java) {
            eachProject.pluginInfo("Ensure that ${this.path} is finalized by jacocoTestReport")
            finalizedBy(eachProject.tasks.named("jacocoTestReport"))
        }

        eachProject.tasks.withType(JacocoReport::class.java) {
            eachProject.pluginInfo("Configuring jacocoTestReport on ${this.path}")
            reports {
                xml.required.set(true)
                csv.required.set(true)
                html.required.set(true)
            }
        }
    }
}
