plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    // The OpenCV Java API is needed to compile, but the implementation is supplied by
    // whoever is running: the openpnp artifact on the JVM, the AAR on Android. Declaring
    // it compileOnly keeps the desktop natives out of the Android build.
    compileOnly(libs.opencv.jvm)
    testImplementation(libs.opencv.jvm)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
    maxHeapSize = "2g"   // full-resolution 12MP Mats

    // Gradle does not forward -D to the forked test JVM, so DumpCorpusTest would never
    // see it. Passed through explicitly.
    systemProperty("dump", providers.systemProperty("dump").getOrElse("false"))
}
