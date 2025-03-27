package io.specmatic.gradle.extensions

interface ObfuscationFeature {
    fun obfuscate(vararg proguardExtraArgs: String?)
}
