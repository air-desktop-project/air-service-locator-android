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
    // AUCUNE bibliothèque HTTP pour l'instant, et c'est un choix en attente :
    // le transport n'est pas arrêté (dépôt serveur, `docs/protocole.md`).
    implementation(project(":coeur-modele"))
    testImplementation(libs.junit)
}
