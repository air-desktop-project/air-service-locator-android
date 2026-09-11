plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.airdesktop.servicelocator.identite"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        allWarningsAsErrors = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    // `api` et non `implementation` : la confirmation prend une `FragmentActivity`
    // d'`androidx.fragment`, que `BiometricPrompt` exige, et ce type fait donc
    // partie de ce que ce module expose.
    api(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
