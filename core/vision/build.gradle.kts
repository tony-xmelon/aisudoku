plugins {
    alias(libs.plugins.kotlin.jvm)
    // Corpus loading is shared with :core:recognize, so it lives in test fixtures
    // rather than being duplicated. Degrade stays in `test`: it touches internal
    // members, and a separate compilation cannot see those.
    `java-test-fixtures`
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
    testFixturesImplementation(libs.opencv.jvm)
    testFixturesApi("org.junit.jupiter:junit-jupiter-api:5.11.4")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
    maxHeapSize = "2g"   // full-resolution 12MP Mats

    // Gradle does not forward -D to the forked test JVM, so DumpCorpusTest would never
    // see it. Passed through explicitly.
    systemProperty("dump", providers.systemProperty("dump").getOrElse("false"))
}
