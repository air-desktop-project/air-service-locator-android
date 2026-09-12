import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.airdesktop.servicelocator"
    compileSdk = 34

    defaultConfig {
        // ── L'IDENTIFIANT EST ARRÊTÉ, ET IL NE SE CHANGERA PLUS ────────────
        //
        // Il suit l'organisation `air-desktop-project` plutôt qu'un domaine de
        // produit : c'est le nom sous lequel les trois dépôts vivent déjà, et
        // celui qui ne dépendra pas d'une bascule de domaine.
        //
        // **UN `applicationId` NE SE CHANGE PLUS** une fois qu'une version a été
        // déposée sur Google Play : le changer produit une application
        // DIFFÉRENTE, que les installations existantes ne mettront jamais à
        // jour. Le fixer maintenant, avant tout dépôt, est la seule occasion de
        // le faire sans coût.
        applicationId = "org.airdesktop.servicelocator"

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
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        // ── L'OUTIL DE CAPTURE, DANS LA VARIANTE DE DÉBOGAGE SEULEMENT ────────
        //
        // `outils-capture/CaptureIntegrity.kt` est un code JETABLE, qui produit un
        // jeton Play Integrity réel pour le serveur. Il est compilé tel quel, à sa
        // place, et n'entre jamais dans une variante `release`.
        getByName("debug") {
            java.srcDirs("src/debug/kotlin", "../outils-capture")
        }
        // ── LE TRANSPORT, EN OBJET PARTAGÉ, DEPUIS LE DÉPÔT CLIENT ────────────
        //
        // `libasl_client_android.so` est PRODUIT par `scripts/construire-mobile.sh`
        // du dépôt `air-service-locator-client`, cloné à côté de celui-ci, et
        // n'est versionné nulle part : c'est une sortie. `arm64-v8a` seulement —
        // c'est ce qu'un téléphone est.
        getByName("main") {
            jniLibs.srcDirs("../../air-service-locator-client/target/mobile/jniLibs")
        }
    }
}

// ── L'ANNUAIRE DE TEST VIENT DE `local.properties` ───────────────────────────
//
// Une adresse sur un réseau, le nom d'un certificat, et la racine qui l'a
// signé : propres à une machine, jamais versionnés. Absents, l'application
// tourne sur le banc en mémoire.
//
//     asl.annuaire.adresse=192.0.2.1:6630
//     asl.annuaire.nom=annuaire
//     asl.annuaire.racines=/chemin/vers/racine.pem
val annuaireDeTest: Triple<String, String, String> = run {
    val fichier = rootProject.file("local.properties")
    if (!fichier.exists()) return@run Triple("", "", "")
    val proprietes = Properties().apply { fichier.inputStream().use { load(it) } }
    val racines = proprietes.getProperty("asl.annuaire.racines", "").let { chemin ->
        if (chemin.isEmpty()) "" else file(chemin).takeIf { it.exists() }?.readText().orEmpty()
    }
    Triple(proprietes.getProperty("asl.annuaire.adresse", ""), proprietes.getProperty("asl.annuaire.nom", ""), racines)
}
android.defaultConfig.buildConfigField("String", "ANNUAIRE_ADRESSE", "\"${annuaireDeTest.first}\"")
android.defaultConfig.buildConfigField("String", "ANNUAIRE_NOM", "\"${annuaireDeTest.second}\"")
android.defaultConfig.buildConfigField("String", "ANNUAIRE_RACINES", "\"${annuaireDeTest.third.replace("\n", "\\n")}\"")

// ── LE NUMÉRO DU PROJET GOOGLE CLOUD VIENT DE `local.properties` ─────────────
//
// Il rattache une demande de jeton Play Integrity à un projet. Ce n'est pas un
// secret, mais c'est une valeur propre à un déploiement, et ce dépôt est public :
// il vit sur la machine, à côté du chemin du SDK, et jamais dans l'historique.
//
//     asl.numeroProjetCloud=123456789012
//
// Absent, il vaut 0 et la capture le dit avant d'échouer.
val numeroProjetCloud: String = run {
    val fichier = rootProject.file("local.properties")
    if (!fichier.exists()) return@run "0"
    // `Properties` importé en tête : dans ce script, `java` désigne l'extension Gradle.
    val proprietes = Properties().apply { fichier.inputStream().use { load(it) } }
    proprietes.getProperty("asl.numeroProjetCloud", "0")
}
android.buildTypes.getByName("debug").buildConfigField("long", "NUMERO_PROJET_CLOUD", "${numeroProjetCloud}L")

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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.google.play.integrity)

    testImplementation(libs.junit)
}
