package io.specmatic.gradle.extensions

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action

interface ShadowingFeature {
    fun shadow(prefix: String? = null, action: Action<ShadowJar>? = Action {})
}
