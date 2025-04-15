package io.specmatic.gradle.versions

import io.specmatic.gradle.SpecmaticGradlePlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ForceVersionConstraintsPluginTest {
    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    fun `it should force dependencies correctly`(
        inputDependency: String,
        expectedDependency: String
    ) {
        val project: Project = ProjectBuilder.builder().build()
        project.group = "org.example"
        project.version = "1.2.3"

        project.plugins.apply("java")
        project.plugins.apply(SpecmaticGradlePlugin::class.java)
        project.repositories.mavenCentral()

        val dependencyConfiguration = project.configurations.create("testConfiguration")
        dependencyConfiguration.dependencies.add(project.dependencies.create(inputDependency))

        project.evaluationDependsOn(":")
        dependencyConfiguration.resolve()

        val resolvedDependency = dependencyConfiguration.resolvedConfiguration.firstLevelModuleDependencies.first()
        assertThat(resolvedDependency.getName()).isEqualTo(expectedDependency)
    }

    companion object {
        @JvmStatic
        fun provideStringsForIsBlank(): List<Arguments?> {
            return ForceVersionConstraintsPlugin.REPLACEMENTS.map { (k, v) ->
                Arguments.of(k, v)
            }
        }
    }
}
