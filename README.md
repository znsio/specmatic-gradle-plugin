# Specmatic gradle plugin

This plugin is used to run Specmatic tests as part of the build process. It ensures some standardization for build
processes across specmatic repositories. Provides the necessary tooling to obfuscate, shadow and seamlessly publish
artifacts to different repositories.

## Usage

```groovy
// in the root project only
plugins {
    id("io.specmatic.gradle") version("A.B.C") // specify version as appropriate
}

specmatic {
    // Provide license details for any libraries that don't have license information in their POM.
    licenseData {
        name = "net.researchgate:gradle-release"
        version = "3.1.0"
        projectUrl = "https://github.com/researchgate/gradle-release"
        license = "MIT"
    }

    // sub projects to obfuscate
    obfuscate(project("core")) {
        // obfuscate task config
    }

    // sub projects to shadow. If obfuscate is also enabled, shadow will run on the obfuscated jar
    shadow(project(":core")) {
        // shadow jar task config
    }

    // choose what type of jars to publish. These only apply if you are obfuscating, or shadowing jars. Ignore if you are not.
    val whatToPublish = listOf(
            PublicationType.OBFUSCATED_ORIGINAL,
            PublicationType.SHADOWED_ORIGINAL,
            PublicationType.SHADOWED_OBFUSCATED
    )

    // sub projects to publish, and configure the pom
    publication(project(":core"), whatToPublish) {
        // configure `MavenPublish`, pom, and other publication settings
        pom {
            name.set("Specmatic License Validation")
            description.set("Specmatic License parsing and validation library")
            url.set("https://specmatic.io")
        }
    }
}
```

```gitignore
# Add the following to the .gitignore file
gen-kt/
gen-resources/
```

## Variables required for publishing

```shell
# API keys from https://central.sonatype.com/
ORG_GRADLE_PROJECT_mavenCentralUsername="..."
ORG_GRADLE_PROJECT_mavenCentralPassword="..."

# The GPG key to sign the artifacts
ORG_GRADLE_PROJECT_signingInMemoryKey="-----BEGIN PGP PRIVATE KEY BLOCK----- ....-----END PGP PRIVATE KEY BLOCK-----"
ORG_GRADLE_PROJECT_signingInMemoryKeyId="abcdef12" # 8 digit key id (last 8 digits of the key)
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="..." # passphrase for the gpg key
```

## Some additional nuances to be aware of

* The plugin will not work if the `specmatic` block is not present in the root project.
* If a project is obfuscated and/or shadowed, the plugin will rename the default `jar` publication to be called
  `original` instead. This is to avoid confusion between the original jar and the obfuscated/shadowed jars. To use this
  dependency in another sibling project, you will need to use the `original` classifier. For example, if you have an
  obfuscated project (with the name `core`), and you want to use it in another project, you will need to add the
  following to the `dependencies` block in the sibling project:
  ```groovy
    dependencies {
        // depend on the original jar of a sibling project
        implementation project(":core", configuration: "maven") // yes, the configuration is "maven". Yak shave for another day
  
        // Other alternatives for the configuration are:
        
        implementation "io.specmatic.blah:flux-capacitor:1.0.0:[obfuscated|all-obfuscated|shadowed|all-shadowed]"
    }
  ```

## Under the hood, what does this provide

* Ensures that libraries use licenses that are not copyleft, or otherwise incompatible for use in a commercial product.
* Ensures some default settings for gradle projects
    * install/configure jacoco
    * configure junit 5
* Configure the project to install release plugins, along with some other useful plugins
* Configure compiler options
    * Use java 17
    * Use UTF-8 encoding for all input files
* Ensure artifacts are reproducible
* Add some metadata to jar manifests that clarify the origin of the jars
    * The name of the project
    * The version of the project
    * The git commit hash
* Ensure that all `Exec` tasks are configured to emit output to the console
* Adds tasks to create shadow jars, if configured
* Adds tasks to obfuscate jars, if configured
* Adds task to publish jars to local maven repository (`publishToMavenLocal`)
* Adds task to publish jars to root project's build dir (`publishAllPublicationsToStagingRepository`)