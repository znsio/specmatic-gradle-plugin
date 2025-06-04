package io.specmatic.gradle.collision

import io.fuchs.gradle.collisiondetector.CollisionDetectorPlugin
import io.fuchs.gradle.collisiondetector.DetectCollisionsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CollisionDetectorPluginWrapper : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(CollisionDetectorPlugin::class.java)
        target.plugins.withType(CollisionDetectorPlugin::class.java) {
            target.tasks.withType(DetectCollisionsTask::class.java) {
                collisionFilter {
                    exclude("OSGI-INF/**")
                    exclude("about.html") // comes from eclipse.jgit
                }
            }
        }
    }
}
