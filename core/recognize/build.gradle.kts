plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:solver"))
    api(project(":core:vision"))
    compileOnly(libs.opencv.jvm)
    testImplementation(libs.opencv.jvm)
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":core:vision")))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
    maxHeapSize = "2g"

    // Gradle does not forward -D to the forked test JVM, so ExportNormalisedTest would
    // never see it. Passed through explicitly, as in :core:vision.
    systemProperty("dump", providers.systemProperty("dump").getOrElse("false"))

    // A folder of photographs to look at that are not in the corpus - see
    // ScanFolderDumpTest. Empty means the test does nothing.
    systemProperty("scan", providers.systemProperty("scan").getOrElse(""))
}
