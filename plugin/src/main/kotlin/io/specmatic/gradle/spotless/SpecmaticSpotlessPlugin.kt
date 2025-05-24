package io.specmatic.gradle.spotless

import com.diffplug.gradle.spotless.BaseKotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class SpecmaticSpotlessPlugin() : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(SpotlessPlugin::class.java)

        target.plugins.withType(SpotlessPlugin::class.java) {

            val spotlessExtension = target.extensions.getByType(SpotlessExtension::class.java)

            spotlessExtension.apply {
                kotlinGradle {
                    target("**/*.kts")
                    configure(this)
                }

                kotlin {
                    target("**/*.kt")
                    configure(this)
                }

                isEnforceCheck = true
            }
        }
    }

    private fun configure(extension: BaseKotlinExtension) {
        extension.apply {
            ktlint().editorConfigOverride(
                mapOf(
                    "max_line_length" to "140",
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_no-line-break-before-assignment" to "disabled",
                    "ktlint_standard_trailing-comma-on-call-site" to "disabled",
                    "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
                    "ktlint_standard_no-blank-lines-in-chained-method-calls" to "disabled",
                ),
            )
        }
    }

}
