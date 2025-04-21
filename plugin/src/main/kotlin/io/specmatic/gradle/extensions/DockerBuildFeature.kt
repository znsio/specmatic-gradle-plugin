package io.specmatic.gradle.extensions

interface DockerBuildFeature {
    fun dockerBuild(vararg dockerBuildArgs: String?)
    fun dockerBuild(imageName: String? = null, vararg dockerBuildArgs: String?)
}
