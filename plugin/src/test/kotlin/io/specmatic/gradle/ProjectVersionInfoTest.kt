package io.specmatic.gradle

import io.specmatic.gradle.versioninfo.ProjectVersionInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ProjectVersionInfoTest {

    @Nested
    inner class ForRootProject {
        @Test
        fun `generates kotlin file path`() {
            val projectVersionInfo = ProjectVersionInfo(
                version = "1.0.0",
                gitCommit = "123456",
                group = "io.specmatic",
                name = "Specmatic-License-Generator",
                kotlinPackageName = "io.specmatic",
                isRootProject = true,
                timestamp = "2021-09-01T12:00:00Z"
            )

            assertThat(projectVersionInfo.packageDir()).isEqualTo("io/specmatic")
            assertThat(projectVersionInfo.kotlinFilePath()).isEqualTo("io/specmatic/VersionInfo.kt")
            assertThat(projectVersionInfo.toKotlinClass()).contains("""val version = "1.0.0"""")
        }

        @Test
        fun `generates properties file path`() {
            val projectVersionInfo = ProjectVersionInfo(
                version = "1.0.0",
                gitCommit = "123456",
                group = "io.specmatic",
                name = "Specmatic-License-Generator",
                kotlinPackageName = "io.specmatic",
                isRootProject = true,
                timestamp = "2021-09-01T12:00:00Z"
            )

            assertThat(projectVersionInfo.propertiesFilePath()).isEqualTo("io/specmatic/version.properties")
            assertThat(projectVersionInfo.toPropertiesFile()).contains("version=1.0.0")
        }

        @Test
        fun `generates kotlin file path when name is empty`() {
            val projectVersionInfo =
                ProjectVersionInfo(
                    version = "1.0.0",
                    gitCommit = "123456",
                    group = "io.specmatic",
                    name = "Specmatic-License-Generator",
                    kotlinPackageName = "io.specmatic",
                    isRootProject = true,
                    timestamp = "2021-09-01T12:00:00Z"
                )

            assertThat(projectVersionInfo.packageDir()).isEqualTo("io/specmatic")
            assertThat(projectVersionInfo.kotlinFilePath()).isEqualTo("io/specmatic/VersionInfo.kt")
            assertThat(projectVersionInfo.toKotlinClass()).contains("""val version = "1.0.0"""")
        }

        @Test
        fun `generates properties file path when name is empty`() {
            val projectVersionInfo =
                ProjectVersionInfo(
                    version = "1.0.0",
                    gitCommit = "123456",
                    group = "io.specmatic",
                    name = "Specmatic-License-Generator",
                    kotlinPackageName = "io.specmatic",
                    isRootProject = true,
                    timestamp = "2021-09-01T12:00:00Z"
                )

            assertThat(projectVersionInfo.propertiesFilePath()).isEqualTo("io/specmatic/version.properties")
            assertThat(projectVersionInfo.toPropertiesFile()).contains("version=1.0.0")
        }
    }

    @Nested
    inner class ForSubProject {
        @Test
        fun `generates kotlin file path`() {
            val projectVersionInfo = ProjectVersionInfo(
                version = "1.0.0",
                gitCommit = "123456",
                group = "io.specmatic.specmatic.license.generator",
                name = "Specmatic-License-Generator",
                kotlinPackageName = "io.specmatic.specmatic.license.generator",
                isRootProject = false,
                timestamp = "2021-09-01T12:00:00Z"
            )

            assertThat(projectVersionInfo.packageDir()).isEqualTo("io/specmatic/specmatic/license/generator")
            assertThat(projectVersionInfo.kotlinFilePath()).isEqualTo("io/specmatic/specmatic/license/generator/VersionInfo.kt")
            assertThat(projectVersionInfo.toKotlinClass()).contains("""val version = "1.0.0"""")
        }

        @Test
        fun `generates properties file path`() {
            val projectVersionInfo = ProjectVersionInfo(
                version = "1.0.0",
                gitCommit = "123456",
                group = "io.specmatic",
                name = "Specmatic-License-Generator",
                kotlinPackageName = "io.specmatic.specmatic.license.generator",
                isRootProject = false,
                timestamp = "2021-09-01T12:00:00Z"
            )

            assertThat(projectVersionInfo.propertiesFilePath()).isEqualTo("io/specmatic/specmatic/license/generator/version.properties")
            assertThat(projectVersionInfo.toPropertiesFile()).contains("version=1.0.0")
        }

        @Test
        fun `generates kotlin file path when name is empty`() {
            val projectVersionInfo =
                ProjectVersionInfo(
                    version = "1.0.0",
                    gitCommit = "123456",
                    group = "io.specmatic",
                    name = "Specmatic-License-Generator",
                    kotlinPackageName = "io.specmatic.specmatic.license.generator",
                    isRootProject = false,
                    timestamp = "2021-09-01T12:00:00Z"
                )

            assertThat(projectVersionInfo.packageDir()).isEqualTo("io/specmatic/specmatic/license/generator")
            assertThat(projectVersionInfo.kotlinFilePath()).isEqualTo("io/specmatic/specmatic/license/generator/VersionInfo.kt")
            assertThat(projectVersionInfo.toKotlinClass()).contains("""val version = "1.0.0"""")
        }

        @Test
        fun `generates properties file path when name is empty`() {
            val projectVersionInfo =
                ProjectVersionInfo(
                    version = "1.0.0",
                    gitCommit = "123456",
                    group = "io.specmatic",
                    name = "Specmatic-License-Generator",
                    kotlinPackageName = "io.specmatic.specmatic.license.generator",
                    isRootProject = false,
                    timestamp = "2021-09-01T12:00:00Z"
                )

            assertThat(projectVersionInfo.propertiesFilePath()).isEqualTo("io/specmatic/specmatic/license/generator/version.properties")
            assertThat(projectVersionInfo.toPropertiesFile()).contains("version=1.0.0")
        }

        @Test
        fun `describe includes short commit for SNAPSHOT release`() {
            val projectVersionInfo = ProjectVersionInfo(
                version = "1.0.0-SNAPSHOT",
                gitCommit = "1234567890abcdef",
                group = "io.specmatic",
                name = "Specmatic-License-Generator",
                kotlinPackageName = "io.specmatic",
                isRootProject = true,
                timestamp = null
            )
            assertThat(projectVersionInfo.toKotlinClass().contains("""fun describe() = "v1.0.0-SNAPSHOT(12345678)""""))
        }

        @Test
        fun `describe excludes short commit for production release`() {
            val projectVersionInfo = ProjectVersionInfo(
                version = "1.0.0",
                gitCommit = "1234567890abcdef",
                group = "io.specmatic",
                name = "Specmatic-License-Generator",
                kotlinPackageName = "io.specmatic",
                isRootProject = true,
                timestamp = "2021-09-01T12:00:00Z"
            )

            assertThat(projectVersionInfo.toKotlinClass().contains("""fun describe() = "v1.0.0 built at 2021-09-01T12:00:00Z""""))
        }

        @Test
        fun `describe includes version only when production release and no timestamp`() {
            val projectVersionInfo = ProjectVersionInfo(
                version = "1.0.0",
                gitCommit = "1234567890abcdef",
                group = "io.specmatic",
                name = "Specmatic-License-Generator",
                kotlinPackageName = "io.specmatic",
                isRootProject = true,
                timestamp = null
            )

            assertThat(projectVersionInfo.toKotlinClass().contains("""fun describe() = "v1.0.0""""))
        }
    }
}
