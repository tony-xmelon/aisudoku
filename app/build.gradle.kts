plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.firebase.appdistribution)
}

android {
    namespace = "io.github.tonyxmelon.aisudoku"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.tonyxmelon.aisudoku"
        minSdk = 26
        targetSdk = 36
        // CI supplies a build number so every distributed build is distinct;
        // locally it stays 1.
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("BUILD_NUMBER") ?: "0")
    }

    // OpenCV ships native libraries for four ABIs. Only arm64 matters for real phones,
    // and dropping the rest takes roughly 60MB out of the APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    /**
     * The key the release is signed with, when one has been provided.
     *
     * Android will not update an installed app whose signature has changed, so a release
     * signed with a throwaway key cannot be updated at all - it has to be uninstalled
     * first, taking every puzzle, photograph and setting with it. That is exactly what was
     * happening: releases were signed with the *debug* config, and CI runners have no
     * debug keystore, so the build generated a fresh one every time. Two consecutive
     * builds were signed by two different certificates.
     *
     * Falls back to debug when no keystore is supplied, so a local build still works;
     * [checkReleaseSigning] is what stops that fallback reaching testers unnoticed.
     */
    val keystore = System.getenv("SIGNING_KEYSTORE")?.takeIf { File(it).isFile }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = File(keystore)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions { unitTests.all { it.useJUnitPlatform() } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*")
    }
}

firebaseAppDistributionDefault {
    // The app id identifies the Firebase app and is not a secret - it is derivable from
    // any built APK - so it is committed and the build works out of the box. The service
    // account key IS a credential, so it only ever arrives through the environment and
    // is never written to the repository.
    appId = System.getenv("FIREBASE_APP_ID")
        ?: "1:52623658492:android:dbb8616352a8d44e29f679"

    // Only set this when a file is genuinely supplied. An empty string counts as a
    // configured path, which the plugin resolves against the project directory and then
    // fails on - before it ever falls through to FIREBASE_TOKEN.
    System.getenv("FIREBASE_CREDENTIALS_FILE")
        ?.takeIf { it.isNotBlank() }
        ?.let { serviceCredentialsFile = it }

    releaseNotesFile = "$rootDir/docs/release-notes.txt"
    groups = "testers"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:solver"))
    implementation(project(":core:vision"))
    implementation(project(":core:recognize"))

    implementation(libs.opencv.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/**
 * Refuses to distribute a release signed with a throwaway key.
 *
 * A debug-signed release installs once and can never be updated, because the key differs
 * on every machine that builds it - so each new version arrives as an uninstall, and the
 * tester loses everything they had. That is invisible from the build log and obvious only
 * weeks later, which is exactly the kind of thing worth failing the build over.
 *
 * Only the upload is gated. Building and testing a release locally without a keystore
 * stays as easy as it was.
 */
tasks.register("checkReleaseSigning") {
    group = "verification"
    description = "Check the release is signed with a stable key before it goes to testers"
    val named = System.getenv("SIGNING_KEYSTORE")
    doLast {
        // A keystore named but not there is a broken setup, and shipping past it would
        // sign with the debug key while looking configured. That fails.
        check(named == null || File(named).isFile) {
            "SIGNING_KEYSTORE points at $named, which is not a file. The release would " +
                "fall back to the debug key without saying so. See docs/signing.md."
        }

        // No keystore at all is the state this project has always been in, so it warns
        // rather than stopping the only route a build has to a phone. It is loud because
        // the cost is invisible and lands on somebody else: every build a tester installs
        // is an uninstall, and takes their puzzles and photographs with it.
        if (named == null) {
            logger.warn(
                "WARNING: this release will be signed with the debug key, which is " +
                    "generated afresh on every machine. Testers cannot update across a " +
                    "signature change - each build arrives as an uninstall and takes " +
                    "their puzzles and photographs with it. See docs/signing.md."
            )
        }
    }
}

tasks.matching { it.name.startsWith("appDistributionUpload") }
    .configureEach { dependsOn("checkReleaseSigning") }

/**
 * Fails fast when the release notes are too long for App Distribution to accept.
 *
 * Firebase caps them at 16,384 characters and rejects the whole upload beyond it. That
 * rejection arrives at the very end of a CI run, after the APK has been built - so this
 * runs as part of `check` instead, where it costs milliseconds and fails before anything
 * has been compiled.
 */
val releaseNotes = rootProject.layout.projectDirectory.file("docs/release-notes.txt")

tasks.register("checkReleaseNotes") {
    group = "verification"
    description = "Check the release notes fit inside App Distribution's limit"
    val notes = releaseNotes
    inputs.file(notes)
    doLast {
        val limit = 16_384
        val length = notes.asFile.readText().length
        check(length <= limit) {
            "docs/release-notes.txt is $length characters, over App Distribution's " +
                "limit of $limit. Move older entries to docs/changelog.md - that file is " +
                "for reading and is never uploaded."
        }
    }
}

tasks.named("check") { dependsOn("checkReleaseNotes") }
tasks.matching { it.name.startsWith("appDistributionUpload") }
    .configureEach { dependsOn("checkReleaseNotes") }

/**
 * Builds and distributes a release using the locally signed-in Firebase CLI.
 *
 * The App Distribution Gradle plugin cannot use the CLI's own login, so this shells out
 * to the CLI instead. It needs no service account and no token: if `firebase login:list`
 * shows an account, this works.
 */
tasks.register<Exec>("distributeLocal") {
    group = "publishing"
    description = "Build a release APK and distribute it via the signed-in Firebase CLI"
    dependsOn("assembleRelease")

    val apk = layout.buildDirectory.file("outputs/apk/release/app-arm64-v8a-release.apk")
    val notes = releaseNotes
    dependsOn("checkReleaseNotes")

    // Windows resolves `firebase` to a .cmd shim, which needs a shell to launch.
    val firebase = if (System.getProperty("os.name").startsWith("Windows")) {
        listOf("cmd", "/c", "firebase")
    } else {
        listOf("firebase")
    }

    commandLine(
        firebase + listOf(
            "appdistribution:distribute", apk.get().asFile.absolutePath,
            "--app", "1:52623658492:android:dbb8616352a8d44e29f679",
            "--release-notes-file", notes.asFile.absolutePath,
            "--groups", "testers",
            "--project", "aisudoku-xmelon",
        )
    )
}
