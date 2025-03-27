package io.specmatic.gradle

import io.specmatic.gradle.extensions.GithubReleaseConfig
import io.specmatic.gradle.license.pluginInfo
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHubBuilder
import java.io.File
import java.io.IOException
import java.nio.file.Files


open class CreateGithubReleaseTask() : DefaultTask() {

    private val files = mutableMapOf<Project, MutableMap<String, String>>()

    fun publish(target: Project, release: GithubReleaseConfig) {
        release.files.keys.forEach { task ->
            dependsOn(target.tasks.getByName(task))
        }
        files.put(target, release.files)
    }


    @TaskAction
    fun createGithubRelease() {
        val user = (System.getenv("ORG_GRADLE_PROJECT_specmaticPrivateUsername")
            ?: throw GradleException("ORG_GRADLE_PROJECT_specmaticPrivateUsername environment variable not set"))
        val password = (System.getenv("ORG_GRADLE_PROJECT_specmaticPrivatePassword")
            ?: throw GradleException("ORG_GRADLE_PROJECT_specmaticPrivatePassword environment variable not set"))

        val githubApi = GitHubBuilder().withEndpoint(System.getenv("GITHUB_API_URL") ?: "https://api.github.com")
            .withPassword(user, password).build()

        val githubRepo = githubApi.getRepository(
            System.getenv("GITHUB_REPOSITORY")
                ?: throw GradleException("GITHUB_REPOSITORY environment variable not set")
        )


        val projectVersion = project.version.toString()

        val githubRelease = findReleaseByName(githubRepo, projectVersion) ?: githubRepo.createRelease(projectVersion)
            .name(projectVersion)
            .draft(true)
            .create()

        for (asset in githubRelease.listAssets()) {
            asset.delete()
        }

        files.forEach { (project, releaseFiles) ->
            releaseFiles.forEach { (taskName, releaseFileName) ->
                val task = project.tasks.getByName(taskName)
                uploadAsset(githubRelease, task.outputs.files.singleFile, releaseFileName)
                project.pluginInfo("Uploaded asset $releaseFileName generated by ${task.path}")
            }
        }

        githubRelease.update().draft(false).update()
    }

    @Throws(IOException::class)
    private fun findReleaseByName(repository: GHRepository, tagOrReleaseName: String?): GHRelease? {
        project.pluginInfo("Looking for release with tag or name $tagOrReleaseName")
        val release = repository.getReleaseByTagName(tagOrReleaseName)
        if (release != null) {
            project.pluginInfo("Found $tagOrReleaseName")
            return release
        }

        for (eachRelease in repository.listReleases()) {
            if (eachRelease.name == tagOrReleaseName) {
                project.pluginInfo("Found $tagOrReleaseName")
                return eachRelease
            }
        }
        project.pluginInfo("Existing release not found")
        return null
    }


    private fun uploadAsset(release: GHRelease, file: File, releaseFileName: String) {
        val mimeType = Files.probeContentType(file.toPath())
        val filename = file.name

        for (existing in release.listAssets()) {
            if (existing.name.startsWith(filename)) {
                logger.info("Replacing existing asset {}", existing.name)
                existing.delete()
            }
        }

        val sha256 = file.inputStream().use { DigestUtils.sha256Hex(it) }

        logger.info("Uploading asset {}", filename)
        file.inputStream().use {
            release.uploadAsset(releaseFileName, it, mimeType)
        }
        release.uploadAsset("${releaseFileName}.SHA256", "$sha256 $releaseFileName".byteInputStream(), "text/plain")
    }
}
