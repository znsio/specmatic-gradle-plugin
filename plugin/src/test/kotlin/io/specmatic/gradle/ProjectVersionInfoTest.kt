package io.specmatic.gradle

import io.specmatic.gradle.versioninfo.ProjectVersionInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProjectVersionInfoTest {
    @Test
    fun `generates kotlin file path`() {
        val projectVersionInfo =
            ProjectVersionInfo("1.0.0", "123456", "io.specmatic", "Specmatic-License-Generator", "2021-09-01T12:00:00Z")

        assertThat(projectVersionInfo.kotlinPackage()).isEqualTo("io.specmatic.specmatic.license.generator")
        assertThat(projectVersionInfo.packageDir()).isEqualTo("io/specmatic/specmatic/license/generator")
        assertThat(projectVersionInfo.kotlinFilePath()).isEqualTo("io/specmatic/specmatic/license/generator/VersionInfo.kt")
        assertThat(projectVersionInfo.toKotlinClass()).contains("val version = \"1.0.0\"")
    }

    @Test
    fun `generates properties file path`() {
        val projectVersionInfo =
            ProjectVersionInfo("1.0.0", "123456", "io.specmatic", "Specmatic-License-Generator", "2021-09-01T12:00:00Z")

        assertThat(projectVersionInfo.propertiesFilePath()).isEqualTo("io/specmatic/specmatic/license/generator/version.properties")
        assertThat(projectVersionInfo.toPropertiesFile()).contains("version=1.0.0")
    }
}