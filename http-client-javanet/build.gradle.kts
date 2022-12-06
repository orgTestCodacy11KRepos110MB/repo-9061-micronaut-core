plugins {
    id("io.micronaut.build.internal.convention-library")
}
dependencies {
    annotationProcessor(project(":inject-java"))
    implementation(project(":http-client-core"))
    implementation(libs.managed.reactor)
}

// Until we upgrade to a version that handles Sealed classes
tasks.named("checkstyleMain") {
    enabled = false
}