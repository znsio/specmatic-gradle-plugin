package io.specmatic.gradle

import com.adarshr.gradle.testlogger.TestLoggerPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport

internal class ConfigureTests(project: Project) {
    init {
        project.plugins.apply(TestLoggerPlugin::class.java)
        project.allprojects.forEach { eachProject: Project ->
            configureProject(eachProject)
        }
    }

    private fun configureProject(eachProject: Project) {
        eachProject.pluginManager.apply(TestLoggerPlugin::class.java)

        if (eachProject.pluginManager.hasPlugin("java")) {
            eachProject.pluginManager.apply("jacoco")
            configureJacoco(eachProject)
        }

        // because we just applied jacoco, the task is not registered just yet
        eachProject.afterEvaluate {
            eachProject.tasks.withType(Test::class.java).configureEach {
                useJUnitPlatform()
                defaultCharacterEncoding = "UTF-8"
                filter.isFailOnNoMatchingTests = true
            }
        }
    }

    private fun configureJacoco(eachProject: Project) {
        eachProject.afterEvaluate {
            tasks.withType(Test::class.java).configureEach {
                finalizedBy(eachProject.tasks.getByName("jacocoTestReport"))
            }

            tasks.withType(JacocoReport::class.java).configureEach {
                reports {
                    xml.required.set(true)
                    csv.required.set(true)
                    html.required.set(true)
                }
            }
        }
    }
}