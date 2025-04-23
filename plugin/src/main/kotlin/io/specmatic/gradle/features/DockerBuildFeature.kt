package io.specmatic.gradle.features

interface DockerBuildFeature {
    fun dockerBuild(block: DockerBuildConfig.() -> Unit)
}

/**
 * Either specify a jar, or a dockerfile must be present in the root project
 */
data class DockerBuildConfig(
    var jar: String? = null,
    var imageName: String? = null,
    var extraDockerArgs: MutableList<String> = mutableListOf(),
)
