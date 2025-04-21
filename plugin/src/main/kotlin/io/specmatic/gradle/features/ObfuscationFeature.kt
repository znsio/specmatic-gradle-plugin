package io.specmatic.gradle.features

interface ObfuscationFeature {
    fun obfuscate(vararg proguardExtraArgs: String?)
}
