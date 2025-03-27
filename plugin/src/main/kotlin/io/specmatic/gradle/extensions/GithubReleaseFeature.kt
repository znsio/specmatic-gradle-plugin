package io.specmatic.gradle.extensions

class GithubReleaseConfig {
    internal val files = mutableMapOf<String, String>()

    fun addFile(task: String, filename: String) {
        files.put(task, filename)
    }
}

interface GithubReleaseFeature {
    var githubRelease: GithubReleaseConfig
    fun githubRelease(block: GithubReleaseConfig.() -> Unit = {})
}

open class GithubReleaseFeatureImpl : GithubReleaseFeature {
    override var githubRelease: GithubReleaseConfig = GithubReleaseConfig()
    override fun githubRelease(block: GithubReleaseConfig.() -> Unit) {
        githubRelease = GithubReleaseConfig().apply(block)
    }
}
