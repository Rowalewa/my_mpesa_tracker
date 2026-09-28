plugins {
    id("com.google.devtools.ksp") version "2.3.11"
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.my_mpesa_tracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.my_mpesa_tracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.geometry)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.compose.material3)
    implementation(libs.places)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core) // for FileProvider
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ksp(libs.room.compiler)
    implementation(libs.androidx.compose.material.icons.extended)
    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

// Coroutines
    implementation(libs.kotlinx.coroutines.android)

// Lifecycle ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

// Firebase Auth + Google Sign-In (Credential Manager)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

// Drive authorization (separate from sign-in — AuthorizationClient, not Credential Manager)
    implementation(libs.play.services.auth)

// Scheduling automatic backup
    implementation(libs.androidx.work.runtime.ktx)
}