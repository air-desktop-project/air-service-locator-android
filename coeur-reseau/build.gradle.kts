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
    // AUCUNE bibliothèque HTTP : le transport est HTTP/3 sur QUIC avec la pile
    // Rust d'`asl-client`, par JNI (`reel/Natif.kt`, `libasl_client_android.so`
    // sous `jniLibs` de l'app). Ce module porte l'interface `Annuaire`, son banc
    // en mémoire, et sa mise en œuvre réelle.
    implementation(project(":coeur-modele"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
