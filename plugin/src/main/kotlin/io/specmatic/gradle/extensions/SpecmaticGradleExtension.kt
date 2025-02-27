package io.specmatic.gradle.extensions

import org.gradle.jvm.toolchain.JavaLanguageVersion

data class ModuleLicenseData(
    val name: String, val version: String, val projectUrl: String?, val license: String, val licenseUrl: String?
)

open class SpecmaticGradleExtension {
    var jvmVersion: JavaLanguageVersion = JavaLanguageVersion.of(17)
        get() = field
        set(value) {
            require(value.asInt() >= 17) { "JVM version must be at least 17" }
            field = value
        }

    val allowedLicenses: MutableList<ModuleLicenseData> = mutableListOf()

    fun allowedLicense(block: ModuleLicenseDataBuilder.() -> Unit) {
        allowedLicenses.add(ModuleLicenseDataBuilder().apply(block).build())
    }
}

class ModuleLicenseDataBuilder {
    var name: String = ""
    var version: String = ""
    var projectUrl: String? = null
    var license: String = ""
    var licenseUrl: String? = null

    fun build(): ModuleLicenseData {
        return ModuleLicenseData(name, version, projectUrl, license, licenseUrl)
    }
}
