plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ch.narro.airservicelocator"
    compileSdk = 34

    defaultConfig {
        // ── L'IDENTIFIANT RESTE À CONFIRMER ────────────────────────────────
        //
        // Il suppose que `narro.ch` est le domaine sous lequel ces applications
        // seront publiées. **UN `applicationId` NE SE CHANGE PLUS** une fois
        // qu'une version a été déposée sur Google Play : le changer produit une
        // application DIFFÉRENTE, que les installations existantes ne mettront
        // jamais à jour.
        applicationId = "ch.narro.airservicelocator"

        // Android 9. C'est le PLANCHER DE `BiometricPrompt` du framework, et le
        // seuil sous lequel la vérification d'identité que ce produit exige
        // n'a pas d'implémentation système cohérente. Descendre plus bas
        // ferait installer l'application sur des appareils qui ne peuvent pas
        // tenir sa condition d'usage.
        minSdk = 28
        targetSdk = 34

        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Un avertissement non traité est une régression qui attend son heure —
        // la même règle que le `RUSTFLAGS: -D warnings` du dépôt serveur.
        allWarningsAsErrors = true
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(project(":coeur-identite"))
    implementation(project(":coeur-reseau"))
    implementation(project(":coeur-modele"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
