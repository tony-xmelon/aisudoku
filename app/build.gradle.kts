import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

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

// The core modules have failed on warnings from the start; the app did not, and had
// quietly accumulated three deprecated arrow icons that do not mirror in a right-to-left
// layout and a LocalLifecycleOwner that had moved. Both are the kind of thing a warning
// is for and nobody reads warnings that do not stop anything.
kotlin {
    compilerOptions { allWarningsAsErrors.set(true) }
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

    // Composed, not the source file: see composeReleaseNotes.
    releaseNotesFile = layout.buildDirectory.file("release-notes.txt").get().asFile.path
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
    // Supplies the non-deprecated LocalLifecycleOwner the camera screen needs.
    implementation(libs.androidx.lifecycle.runtime.compose)
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

        // Now that a key exists, no key is a fault rather than a state of affairs. This
        // warned while there was none, so as not to take away the only route a build had
        // to a phone; stopping the upload is the right answer once the alternative is
        // simply to set the secrets. A release testers cannot update is worse than no
        // release: it costs them everything they had.
        check(named != null) {
            "This release would be signed with the debug key, which is generated afresh " +
                "on every machine, so testers cannot update over it - they must uninstall " +
                "and lose their puzzles and photographs. The signing secrets are missing. " +
                "See docs/signing.md."
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
        // What is uploaded is this file with a generated header on top, so the room the
        // header takes comes off the limit here. Two lines and a blank one; 200 is
        // several times what it needs and still leaves 16,000 characters of prose.
        val header = 200
        val limit = 16_384 - header
        val length = notes.asFile.readText().length
        check(length <= limit) {
            "docs/release-notes.txt is $length characters, over App Distribution's " +
                "limit of 16384 less $header for the version header. Move older entries " +
                "to docs/changelog.md - that file is for reading and is never uploaded."
        }
    }
}

tasks.named("check") { dependsOn("checkReleaseNotes") }
tasks.matching { it.name.startsWith("appDistributionUpload") }
    .configureEach { dependsOn("checkReleaseNotes") }

/**
 * Puts the version and the change that produced it at the top of the notes.
 *
 * Seventeen builds went out carrying notes about a corpus change from two days earlier,
 * because the prose is written by hand and nothing failed when it was not. Prose worth
 * reading cannot be generated, so this does not try: it prepends the two facts that can
 * be, and those two are enough to tell whether the rest is about the build in hand.
 *
 * The subject comes from git, which CI has - a shallow clone still has the commit it
 * checked out. When there is no git at all the header keeps the version and drops the
 * subject, because a release must never fail over its own notes.
 */
val composedNotes = layout.buildDirectory.file("release-notes.txt")

tasks.register("composeReleaseNotes") {
    group = "publishing"
    description = "Prepend the version and the last change to the notes sent to testers"
    val source = releaseNotes
    val target = composedNotes
    val version = "0.1." + (System.getenv("BUILD_NUMBER") ?: "0")
    val root = rootProject.layout.projectDirectory.asFile
    inputs.file(source)
    outputs.file(target)
    doLast {
        val subject = runCatching {
            val process = ProcessBuilder("git", "log", "-1", "--pretty=%s")
                .directory(root)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
                .takeIf { process.waitFor() == 0 }
        }.getOrNull()

        val file = target.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("AI Sudoku $version")
                if (!subject.isNullOrBlank()) appendLine(subject)
                appendLine()
                append(source.asFile.readText())
            }
        )
    }
}

tasks.matching { it.name.startsWith("appDistributionUpload") }
    .configureEach { dependsOn("composeReleaseNotes") }

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

/**
 * Refuses to ship native libraries that are not 16 KB page aligned.
 *
 * Play requires this of anything with native code, and a library that fails it does not
 * misbehave here - it fails to load on a 16 KB device, which is a phone this build was
 * never run on. OpenCV 4.11.0 was exactly that case: its own libopencv_java4.so was
 * aligned correctly while the libc++_shared.so packaged beside it was still on 4 KB, so
 * the fault sat in a dependency's packaging rather than anywhere in this repository.
 *
 * Reading the ELF program headers is the whole check: every PT_LOAD segment of every
 * 64-bit library has to declare an alignment of at least 16384. Nothing else in the build
 * would notice, which is the reason to look.
 */
tasks.register("checkNativeAlignment") {
    group = "verification"
    description = "Check every native library is 16 KB page aligned, as Play requires"

    // Inspecting the built APK means depending on the build that makes it. Without this
    // Gradle is free to run the check first and pass on a stale APK from an earlier
    // version - which it duly did, reporting success on libraries it had not looked at.
    dependsOn("assembleRelease")

    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    inputs.dir(apkDir)
    doLast {
        val apks = apkDir.get().asFile.listFiles { f: File -> f.extension == "apk" }.orEmpty()
        check(apks.isNotEmpty()) { "No release APK to inspect in ${apkDir.get().asFile}." }

        val misaligned = mutableListOf<String>()
        for (apk in apks) {
            ZipFile(apk).use { zip ->
                for (entry in zip.entries().asSequence()) {
                    if (!entry.name.endsWith(".so")) continue
                    val bytes = zip.getInputStream(entry).readBytes()
                    // 64-bit ELF only: the 16 KB rule applies to the 64-bit ABIs.
                    if (bytes.size < 64) continue
                    val elf = bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() &&
                        bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
                    if (!elf || bytes[4].toInt() != 2) continue

                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val headerOffset = buffer.getLong(0x20)
                    val entrySize = buffer.getShort(0x36).toInt()
                    val count = buffer.getShort(0x38).toInt()
                    for (i in 0 until count) {
                        val at = (headerOffset + i * entrySize).toInt()
                        if (buffer.getInt(at) != 1) continue          // PT_LOAD only
                        val align = buffer.getLong(at + 48)
                        if (align < 16384) {
                            misaligned += "${entry.name} in ${apk.name} is aligned to " +
                                "$align, not 16384"
                        }
                    }
                }
            }
        }
        check(misaligned.isEmpty()) {
            "These native libraries would fail to load on a 16 KB page device, and Play " +
                "rejects them:\n  " + misaligned.distinct().joinToString("\n  ") +
                "\nThe alignment comes from whoever packaged the library, so the fix is " +
                "normally a dependency version rather than anything here."
        }
    }
}

tasks.matching { it.name.startsWith("appDistributionUpload") }
    .configureEach { dependsOn("checkNativeAlignment") }
