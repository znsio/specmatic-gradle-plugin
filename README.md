# Specmatic gradle plugin

This plugin is used to run Specmatic tests as part of the build process. It ensures some standardization for build
processes across specmatic repositories.

## Usage

```groovy
plugins {
    id("io.specmatic.gradle") version ("A.B.C") // specify version as appropriate
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