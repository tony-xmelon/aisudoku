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

    // The corpus is an input to these tests even though it is not on the compile path,
    // and Gradle cannot know that. Without it a task stays up to date when a photograph
    // is added, so a new page can break a test and the build still reports success -
    // which is exactly what happened when the newsprint pages arrived. A file tree of a
    // directory that does not exist is simply empty, so CI is unaffected.
    inputs.files(rootProject.fileTree("corpus"), rootProject.fileTree("corpus-labels"))
        .withPropertyName("corpus")
        .optional()
}
