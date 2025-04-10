# Specmatic gradle obfuscation and fatjar plugin

Obfuscation and shadowing plugin for dummies! Setting up obfuscation, getting the shadow jar, and publishing to maven
central is now a breeze! Just add this plugin, configure your intent using the `specmatic` block (more below), and the
plugin will take care of the rest. Batteries included. Just provide the credentials for signing and publishing.

## Features

1. Auto signing/publishing of artifacts
    - maven central
    - maven local
    - specmatic private repository (on github)
    - any other supported URLs/repositories
2. License checks
    - Ensure that dependencies have a license that allows commecial use of specmatic software (i.e. no copy left
      licenses) without any incumberance.
    - Generate a license report that can be packaged in the distributable jar. This is legal requirement from licenses
      like Apache, BSD-3-Clause. These licenses have a clause that requires distributions of software to carry a notice,
      or attribution specified in the license.
3. Pretty print test progress - uses https://github.com/radarsh/gradle-test-logger-plugin
4. Publish artifacts and create GitHub releases
    - uses https://github.com/researchgate/gradle-release to create git tags
    - uses API to create a GitHub release
5. Print task info and dependencies - uses https://gitlab.com/barfuin/gradle-taskinfo
6. Print vulnerability scan reports - uses [osv-scanner](https://github.com/google/osv-scanner)
7. Creates a `version.properties` and `VersionInfo.kt` file in the `${groupId}:${projectName}` package. This contains
   details like the version number, git sha. That may be useful for `--version` or just dumping the version at startup.
8. Ensure that java and kotlin compilation is forced to a configured and consistent version across projects.
9. Ensure artifacts are reproducible.
10. Pretty print any `exec` or `javaexec` tasks, along with their outputs
11. Auto-upgrade/migrated deprecated dependencies to newer dependencies.
12. Better integration with sample repositories
    - Run a build against sample projects and validate changes.
    - Bummp version of dependency in sample project. Ensure that the appropriate jar is checked into the sample repo.

## Requirements for using this plugin

1. The following environment variables containing secrets are needed based on the
   requirements. [This script](https://github.com/znsio/specmatic-github-workflows/blob/main/bin/upload-secrets) will
   help you upload the relevant secrets by scanning your github workflows.

   | Variable(s)                                                                                                                                 | Purpose                                                                                                        | 
   |---------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
   | **Maven Central**                                                                                                                           |                                                                                                                |
   | `ORG_GRADLE_PROJECT_mavenCentralUsername`                                                                                                   | Username for Maven Central                                                                                     |
   | `ORG_GRADLE_PROJECT_mavenCentralPassword`                                                                                                   | Password for Maven Central                                                                                     |
   | **Signing**                                                                                                                                 |                                                                                                                |
   | `ORG_GRADLE_PROJECT_signingInMemoryKey`                                                                                                     | GPG private key for signing (ascii armoured/base64 encoded, without the leading/trailing -----BEGIN/END lines) |
   | `ORG_GRADLE_PROJECT_signingInMemoryKeyId`                                                                                                   | GPG key ID (last 8 chars of hex hex key without the leading `0x`)                                              |
   | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`                                                                                             | Passphrase for the GPG key                                                                                     |
   | **Specmatic Private Repo**                                                                                                                  |                                                                                                                |
   | `ORG_GRADLE_PROJECT_specmaticPrivateUsername`                                                                                               | Username for Specmatic private repository                                                                      |
   | `ORG_GRADLE_PROJECT_specmaticPrivatePassword`                                                                                               | Password for Specmatic private repository                                                                      |
   | **Docker Hub**                                                                                                                              |                                                                                                                |
   | No variables are needed, but you are required to perform a docker login yourself. The plugin will simply execute a `docker push` equivalent |                                                                                                                |

## Installation

1. Edit `build.gradle[.kts]`
   ```kotlin
   // in the root project only
   plugins {
      // version specified in settings.gradle & gradle.properties
       id("io.specmatic.gradle")
   }
   ```

2. Edit `settings.gradle[.kts]`
   ```kotlin
   pluginManagement {
      val specmaticGradlePluginVersion = settings.extra["specmaticGradlePluginVersion"] as String
      plugins {
          id("io.specmatic.gradle") version(specmaticGradlePluginVersion)
      }
   }
   ```

3. Edit `gradle.properties`
   ```properties
   specmaticGradlePluginVersion=0.1.4
   ```
   
## Usage/Configuration

1. Add the following to your `build.gradle[.kts]` file
    ```kotlin
    specmatic {
        // Set the JVM version. Currently defaults to 17
        jvmVersion = JavaLanguageVersion.of(17)
        // Set the kotlin version to be used. Currently defaults to 1.9.25
        kotlinVersion = "1.9.25"
        // Set the kotlin compiler version. Currently defaults to 1.9
        kotlinApiVersion = KotlinVersion.KOTLIN_1_9
        // List of sample projects that need validation before release, and bumping post release
        downstreamDependentProjects = listOf("project1", "project2")
    
        // Publish this to some repositories. Can be invoked multiple times
        publishTo("internalRepo", "https://internal.repo.url/repository/maven-releases/")
        // Publish this to maven central. Only use this on open source code
        publishToMavenCentral()
    
        // Provide license details for any libraries that don't have license information in their POM.
        // if using groovy, you may need to prefix below lines with `it.XXX` instead
        licenseData {
            name = "net.researchgate:gradle-release"
            version = "3.1.0"
            projectUrl = "https://github.com/researchgate/gradle-release"
            license = "MIT"
        }
    
        `with<Commercial|OSS><Library|Application|ApplicationLibrary>`(project(":bar")) {
            // The main class, if publishing an application variant
            mainClass = "io.specmatic.ExampleApp"
    
            // Create a GitHub release. Upload any files generated by specified tasks.
            githubRelease {
                addFile("sourcesJar", "foo-sources-${version}.jar")
            }
    
            // Create a docker build/publish task. Pass any optional args to the docker build task. 
            // The `--build-arg VERSION` is already passed as a default 
            dockerBuild("--extra", "--docker", "--args")
    
            // Obfuscation is enabled by default, but you may pass additional proguard args https://www.guardsquare.com/manual/configuration/usage
            obfuscate("-some-arg")
            obfuscate("-more-args", "-some-more-args")
    
            // Shadowing is enabled, but you pass any additional shadowing options - https://gradleup.com/shadow/
            shadow(prefix = "specmatic_foo") {
                minimize()
                // other options...
            }
    
            publish {
                // configure the pom and any other publication settings
                pom {
                    name.set("Specmatic License Validation")
                    description.set("Specmatic License parsing and validation library")
                    url.set("https://specmatic.io")
                }
            }
        }
    }
    ```

2. Setup your `.gitignore`
    ```gitignore
    # Add the following to the .gitignore file
    gen-kt/
    gen-resources/
    ```
3. Setup GitHub workflows. Best to copy/paste from existing workflows.

## Some additional nuances to be aware of

- The plugin will not work if the `specmatic` block is not present in the root project.
- If a project is obfuscated and/or shadowed, the plugin will rename the default `jar` publication to be called
  `original` instead. This is to avoid confusion between the original jar and the obfuscated/shadowed jars. To use this
  dependency in another sibling project, you will need to use the `original` classifier. For example, if you have an
  obfuscated project (with the name `core`), and you want to use it in another project, you will need to add the
  following to the `dependencies` block in the sibling project:

  ```groovy
    dependencies {
        // depend on the original jar of a sibling project
        implementation project(":core")

        // Other alternatives for the configuration are:
        implementation "io.specmatic.blah:flux-capacitor-[original|obfuscated-original|shadow-obfuscated|shadow-original]:1.0.0"
    }
  ```

## Available tasks

Here is a list of available tasks

| Task                                                 | Description                                                                                                                                                   |
|------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **License Checks**                                   |                                                                                                                                                               |
| `checkLicense`                                       | Check if License could be used                                                                                                                                |
| `generateLicenseReport`                              | Generates license report for all dependencies of this project and its subprojects.                                                                            |
| **Publishing tasks**                                 |                                                                                                                                                               |
| `publishAllPublicationsToMavenCentralRepository`     | Publishes all Maven publications produced by this project to the mavenCentral repository.                                                                     |
| `publishAllPublicationsToSpecmaticPrivateRepository` | Publishes all Maven publications produced by this project to the specmaticPrivate repository.                                                                 |
| `publishAllPublicationsToStagingRepository`          | Publishes all Maven publications produced by this project to the staging repository.                                                                          |
| **Release tasks**                                    |                                                                                                                                                               |
| `afterReleaseBuild`                                  | Runs immediately after the build when doing a release. Install task dependencies on this task to be executed after a release build. For e.g. `uploadArchives` |
| `beforeReleaseBuild`                                 | Runs immediately before the build when doing a release. Install task dependencies on this task to be execute before a release build. For e.g. `check`         |
| `publishToMavenCentral`                              | Publishes to a staging repository on Sonatype OSS.                                                                                                            |
| `release`                                            | Verify project, release, and update version to next.                                                                                                          |
| **Vulnerability tasks**                              |                                                                                                                                                               |
| `vulnScanJar`                                        | Scan for vulnerabilities in jars.                                                                                                                             |
| `vulnScanJarPrint`                                   | Print vulnerabilities in vulnScanJar.                                                                                                                         |
| `vulnScanDocker`                                     | Scan for vulnerabilities in docker image.                                                                                                                     |
| `vulnScanDockerPrint`                                | Print vulnerabilities in vulnScanDocker.                                                                                                                      |
| **Docker tasks**                                     |                                                                                                                                                               |
| `dockerBuild`                                        | Builds the docker image (for local use)                                                                                                                       |
| `dockerBuildxPublish`                                | Builds and publishes `linux/amd64,linux/arm64` variants of the docker image                                                                                   | 
| **Downstream Project Validation**                    |                                                                                                                                                               |
| `validateDownstreamProjects`                         | Validate downstream project(s)                                                                                                                                |
| `bumpVersionsInDownstreamProjects`                   | Bump versions in downstream project(s)                                                                                                                        | 
| `fetchArtifactsInDownstreamProjects`                 | Fetch artifacts downstream project(s)                                                                                                                         | 
| **Internal tasks**                                   |                                                                                                                                                               |
| `createGithubRelease`                                | Create a Github release. This is already wired up when publishing a release.                                                                                  |
| `cyclonedxBom`                                       | Generates a CycloneDX compliant Software Bill of Materials (SBOM).                                                                                            |

## Available distribution flavours and the artifacts they generate

| Generated artifact(s)                                                           | Obfuscated | Fat/Shadowed/Shaded | Has dependencies in POM | Javadoc/Source Jars | Is executable | Purpose                                                                         |
|:--------------------------------------------------------------------------------|:----------:|:-------------------:|:-----------------------:|:-------------------:|:-------------:|---------------------------------------------------------------------------------|
| **OSSLibraryConfig**                                                            |            |                     |                         |                     |               |                                                                                 |
| `${groupId}:${projectId}`                                                       |  &#x274C;  |      &#x274C;       |        &#x2705;         |      &#x2705;       |   &#x274C;    | Publishing a library (specmatic-junit5, for e.g.)                               |
| **OSSApplicationConfig**                                                        |            |                     |                         |                     |               |                                                                                 |
| `${groupId}:${projectId}`                                                       |  &#x274C;  |      &#x2705;       |        &#x274C;         |      &#x2705;       |   &#x2705;    | Publishing an application (specmatic-executable, for e.g.)                      |
| **OSSApplicationLibraryConfig**                                                 |            |                     |                         |                     |               |                                                                                 |
| `${groupId}:${projectId}`                                                       |  &#x274C;  |      &#x2705;       |        &#x274C;         |      &#x2705;       |   &#x2705;    | Publishing an application (specmatic-executable-all, for e.g.)                  |
| `${groupId}:${projectId}-min`                                                   |  &#x274C;  |      &#x274C;       |        &#x2705;         |      &#x2705;       |   &#x2705;    | Use the application code as a library (specmatic-executable, for e.g.)          |
| **CommercialLibraryConfig**                                                     |            |                     |                         |                     |               |                                                                                 |
| `${groupId}:${projectId}`                                                       |  &#x2705;  |      &#x2705;       |        &#x274C;         |      &#x274C;       |   &#x2705;    | Publish a commercial library, for use in other modules (license core, for e.g.) |
| `${groupId}:${projectId}-all-debug`                                             |  &#x274C;  |      &#x2705;       |        &#x274C;         |      &#x274C;       |   &#x2705;    | For local debugging, above jar, but unobfuscated                                |
| `${groupId}:${projectId}-min`                                                   |  &#x2705;  |      &#x274C;       |        &#x2705;         |      &#x274C;       |   &#x274C;    | Obfuscated, but has dependencies in POM, for local debugging                    |         |
| `${groupId}:${projectId}-core-dont-use-this-unless-you-know-what-you-are-doing` |  &#x274C;  |      &#x274C;       |        &#x2705;         |      &#x274C;       |   &#x274C;    | Original jar + original deps in the POM, for local debugging                    |
| **CommercialApplicationConfig**                                                 |  &#x2705;  |      &#x2705;       |                         |                     |               |                                                                                 |
| `${groupId}:${projectId}`                                                       |  &#x2705;  |      &#x2705;       |        &#x274C;         |      &#x274C;       |   &#x2705;    | Publish this for end user consumption                                           |
| `${groupId}:${projectId}-all-debug`                                             |  &#x274C;  |      &#x2705;       |        &#x274C;         |      &#x274C;       |   &#x2705;    | For local debugging                                                             |
| **CommercialApplicationAndLibraryConfig**                                       |  &#x2705;  |      &#x2705;       |                         |                     |               |                                                                                 |
| `${groupId}:${projectId}`                                                       |  &#x2705;  |      &#x2705;       |        &#x274C;         |      &#x274C;       |   &#x2705;    | Publish this for end user consumption as an executable                          |
| `${groupId}:${projectId}-all-debug`                                             |  &#x274C;  |      &#x2705;       |        &#x274C;         |      &#x274C;       |   &#x2705;    | For local debugging                                                             |
| `${groupId}:${projectId}-all-min`                                               |  &#x2705;  |      &#x274C;       |        &#x2705;         |      &#x274C;       |   &#x274C;    | Publish this for end user consumption as as library                             |
