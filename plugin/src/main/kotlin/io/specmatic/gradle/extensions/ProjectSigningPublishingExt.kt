package io.specmatic.gradle.extensions

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishBasePlugin
import com.vanniktech.maven.publish.SonatypeHost
import io.specmatic.gradle.jar.massage.publishing
import io.specmatic.gradle.license.pluginInfo
import io.specmatic.gradle.specmaticExtension
import org.gradle.api.Project
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

internal fun Project.configureSigning() {
    plugins.apply(SigningPlugin::class.java)

    plugins.withType(SigningPlugin::class.java) {
        extensions.getByType(SigningExtension::class.java).apply {
            val keyId = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId")
            val key = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
            val password = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
            useInMemoryPgpKeys(keyId, key, password)
        }

        tasks.withType(Sign::class.java) {
            isRequired = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId") != null
        }
    }
}

internal fun Project.configurePublishing() {
    plugins.apply(MavenPublishBasePlugin::class.java)
    plugins.withType(MavenPublishBasePlugin::class.java) {
        pluginInfo("Configuring maven publishing on $project")
        setupPublishingTargets()
    }
}

private fun Project.setupPublishingTargets() {
    val stagingRepo = project.uri(project.rootProject.layout.buildDirectory.dir("mvn-repo"))

    project.extensions.getByType(MavenPublishBaseExtension::class.java).apply {
        val specmaticExtension = project.rootProject.specmaticExtension()

        val publishTargets = specmaticExtension.publishTo + listOf(
            MavenInternal(
                "staging", stagingRepo
            )
        )

        publishTargets.forEach { publishTarget ->
            if (publishTarget is MavenCentral) {
                publishToMavenCentral(SonatypeHost.Companion.CENTRAL_PORTAL, false)
            } else if (publishTarget is MavenInternal) {
                val repo = publishTarget
                project.pluginInfo("Configuring publishing to ${repo.repoName} with url ${repo.url}")
                publishing.repositories.maven {
                    name = repo.repoName
                    url = repo.url
                    if (url.scheme != "file") {
                        credentials(PasswordCredentials::class.java)
                    }
                }
            } else {
                project.pluginInfo("publishToMavenCentral is not set. Not publishing to Maven Central")
            }
        }
        signAllPublications()
    }

}
