package io.specmatic.gradle.features

interface DockerBuildFeature {
    fun dockerBuild(vararg dockerBuildArgs: String?)
    fun dockerBuild(imageName: String? = null, vararg dockerBuildArgs: String?)
}
