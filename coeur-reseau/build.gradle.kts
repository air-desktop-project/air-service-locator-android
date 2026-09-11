plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.airdesktop.servicelocator.reseau"
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
    // AUCUNE bibliothèque HTTP, et ce n'est plus une attente : le transport est
    // HTTP/3 sur QUIC avec la pile Rust d'`asl-client`, qui arrivera par JNI. En
    // attendant, ce module porte l'interface `Annuaire` et son banc en mémoire.
    implementation(project(":coeur-modele"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
