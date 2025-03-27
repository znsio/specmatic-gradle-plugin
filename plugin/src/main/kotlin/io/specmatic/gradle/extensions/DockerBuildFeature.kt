package io.specmatic.gradle.extensions

interface DockerBuildFeature {
    fun dockerBuild(vararg dockerBuildArgs: String?)
}

open class DockerBuildFeatureImpl : DockerBuildFeature {
    internal var dockerBuildExtraArgs = mutableListOf<String?>()
    override fun dockerBuild(vararg dockerBuildArgs: String?) {
        this.dockerBuildExtraArgs.addAll(dockerBuildArgs)
    }
}
