package io.specmatic.gradle.features

interface ApplicationFeature : DistributionFlavor {
    var mainClass: String
}
