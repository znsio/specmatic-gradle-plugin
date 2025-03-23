package io.specmatic.gradle.extensions

interface DockerBuildFeature {
    fun dockerBuild(vararg dockerBuildArgs: String?)
}
