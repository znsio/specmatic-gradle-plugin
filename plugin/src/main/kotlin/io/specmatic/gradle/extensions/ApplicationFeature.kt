package io.specmatic.gradle.extensions

interface ApplicationFeature : DistributionFlavor {
    var mainClass: String
}
