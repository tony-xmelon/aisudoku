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
        versionCode = 1
        versionName = "0.1"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*")
    }
}

firebaseAppDistribution {
    // The service account key is a credential and is never committed; CI supplies it
    // through the GOOGLE_APPLICATION_CREDENTIALS environment variable.
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
}
