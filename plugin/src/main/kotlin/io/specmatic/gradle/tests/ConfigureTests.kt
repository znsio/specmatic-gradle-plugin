package io.specmatic.gradle.tests

import io.specmatic.gradle.pluginDebug
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport

internal class ConfigureTests(project: Project) {
    init {
        project.allprojects.forEach { eachProject: Project ->
            configureProject(eachProject)
        }
    }

    private fun configureProject(eachProject: Project) {
        pluginDebug("Configuring test logger on $eachProject")
        eachProject.pluginManager.apply("com.adarshr.test-logger")

        eachProject.pluginManager.withPlugin("java") {
            eachProject.pluginManager.apply("jacoco")
            eachProject.pluginManager.withPlugin("jacoco") {
                configureJacoco(eachProject)
                configureJunit(eachProject)
            }
        }

    }

    private fun configureJunit(eachProject: Project) {
        pluginDebug("Configuring junit on $eachProject")

        eachProject.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            defaultCharacterEncoding = "UTF-8"
            filter.isFailOnNoMatchingTests = true
        }
    }

    private fun configureJacoco(eachProject: Project) {
        pluginDebug("Configuring jacoco on $eachProject")

        eachProject.tasks.withType(Test::class.java).configureEach {
            pluginDebug("Ensure that ${this.path} is finalized by jacocoTestReport")
            finalizedBy(eachProject.tasks.getByName("jacocoTestReport"))
        }

        eachProject.tasks.withType(JacocoReport::class.java).configureEach {
            pluginDebug("Configuring jacocoTestReport on ${this.path}")
            reports {
                xml.required.set(true)
                csv.required.set(true)
                html.required.set(true)
            }
        }
    }
}