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

    // The corpus is an input to these tests even though it is not on the compile path,
    // and Gradle cannot know that. Without it a task stays up to date when a photograph
    // is added, so a new page can break a test and the build still reports success -
    // which is exactly what happened when the newsprint pages arrived. A file tree of a
    // directory that does not exist is simply empty, so CI is unaffected.
    inputs.files(rootProject.fileTree("corpus"), rootProject.fileTree("corpus-labels"))
        .withPropertyName("corpus")
        .optional()

    // A folder of photographs to look at that are not in the corpus - see
    // ScanFolderDumpTest. Empty means the test does nothing.
    systemProperty("scan", providers.systemProperty("scan").getOrElse(""))

    // Where ScanFolderDumpTest writes the straightened grid of each photograph, for
    // reading by eye. Empty means it writes none.
    systemProperty("write", providers.systemProperty("write").getOrElse(""))
}
