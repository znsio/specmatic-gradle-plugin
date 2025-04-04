package io.specmatic.gradle.utils

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import org.gradle.api.Project

fun gitSha(project: Project): String {
    return runCatching {
        val rootProject = project.rootProject
        val reader = SystemReader.getInstance()
        SystemReader.setInstance(object : SystemReader.Delegate(reader) {
            override fun openSystemConfig(parent: Config?, fs: FS?): FileBasedConfig {
                return object : FileBasedConfig(parent, null, fs) {
                    override fun load() {}

                    override fun isOutdated(): Boolean {
                        return false
                    }
                }
            }
        })
        Git.open(rootProject.rootDir).use {
            Git.open(rootProject.rootDir).repository.resolve("HEAD").name
        }
    }.getOrElse { "unknown - no git repo found" }
}
