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

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }

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
    serviceCredentialsFile = System.getenv("FIREBASE_CREDENTIALS_FILE") ?: ""
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

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
