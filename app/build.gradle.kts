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

        // ── LA VERSION, EN UN SEUL ENDROIT ───────────────────────────────
        //
        // Semver, `MAJEURE.MINEURE.CORRECTIF`. **Chaque PR la change**, dans le
        // commit qui porte le changement — la CI compare ces deux lignes à
        // celles de `main` et refuse une PR qui ne les a pas touchées. Elle se
        // lit à l'écran (Compte › Annuaire), et c'est ce qu'un utilisateur
        // cite quand il rapporte quelque chose. `versionCode` est l'entier
        // croissant que le Play Store exige distinct à chaque envoi.
        versionCode = 36
        versionName = "0.20.0"
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
        // `src/debug/kotlin/.../capture/` produit une chaîne d'attestation de clé
        // réelle pour le serveur (`docs/attestation/capture-keystore.md`, dépôt
        // serveur). C'est une fonction du système — aucun SDK de services, C19 —
        // et elle n'entre jamais dans une variante `release`.
        getByName("debug") {
            java.srcDirs("src/debug/kotlin")
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
// Des racines sur un réseau, désignées par leur IDENTITÉ : propres à une
// machine, jamais versionnées. Absentes, l'application tourne sur le banc en
// mémoire.
//
// Les racines — celles entre lesquelles l'utilisateur choisit dans
// Compte › Annuaire — se donnent par un fichier `annuaire.json`, LA MÊME FORME
// que celui de l'application iOS (voir `ListeDAnnuaires`) :
//
//     asl.annuaire.liste=/chemin/vers/annuaire.json
//
// **Chaque entrée désigne sa racine par son identité** (`"annuaire":"n-…"` et
// des `"locateurs"` littéraux, ou `"racines":[…]` pour une entrée qui en couvre
// plusieurs) : aucun nom n'est résolu (C20), et une racine ne se croit que par
// sa clé (décision 58, transition achevée). Il n'y a plus d'autorité PEM :
// `asl.annuaire.racines`, s'il reste, est ignoré avec un avertissement.
// L'ancienne forme d'une seule racine par son nom (`asl.annuaire.adresse`,
// `asl.annuaire.nom`) ne porte aucune identité : seule, elle est refusée ;
// à côté de `liste`, ignorée avec un avertissement.
//
// Le fichier est vérifié ICI : un JSON illisible, ou une liste dont AUCUNE
// entrée n'est identifiée, fait échouer la construction, plutôt que de livrer
// une application qui n'aurait rien à joindre. Une entrée sans identité parmi
// d'autres est laissée de côté par l'application, qui le journalise. Ce qui
// passe dans `BuildConfig`, c'est le JSON réécrit sur une ligne ; l'application
// le relit.
val annuaireDeTest: String = run {
    val fichier = rootProject.file("local.properties")
    if (!fichier.exists()) return@run ""
    val proprietes = Properties().apply { fichier.inputStream().use { load(it) } }
    if (proprietes.getProperty("asl.annuaire.racines", "").isNotEmpty()) {
        logger.warn("asl.annuaire.racines est ignoré : les racines ne se croient plus que par leur identité (décision 58)")
    }
    val liste = proprietes.getProperty("asl.annuaire.liste", "")
    if (proprietes.getProperty("asl.annuaire.adresse", "").isNotEmpty()) {
        if (liste.isEmpty()) {
            throw GradleException(
                "asl.annuaire.adresse : l'ancienne forme par le nom ne porte aucune identité — " +
                    "donner asl.annuaire.liste, un annuaire.json dont les entrées ont `annuaire` n-… et des `locateurs` littéraux",
            )
        }
        logger.warn("asl.annuaire.adresse et asl.annuaire.nom sont ignorés : asl.annuaire.liste les remplace")
    }
    if (liste.isEmpty()) return@run ""
    val source = file(liste)
    require(source.exists()) { "asl.annuaire.liste : « $liste » n'existe pas" }
    val json: Any? = try {
        groovy.json.JsonSlurper().parseText(source.readText())
    } catch (e: Exception) {
        throw GradleException("asl.annuaire.liste : « $liste » n'est pas du JSON lisible (${e.message})")
    }
    // Une entrée est identifiée si elle porte `annuaire` n-… et des `locateurs`,
    // ou des `racines` qui les portent. Le détail (locateurs littéraux) est
    // vérifié par `ListeDAnnuaires` ; ici, on refuse une liste vide d'identités.
    fun identifiee(e: Any?): Boolean {
        val m = e as? Map<*, *> ?: return false
        val propre = (m["annuaire"] as? String)?.startsWith("n-") == true && (m["locateurs"] as? List<*>)?.isNotEmpty() == true
        return propre || (m["racines"] as? List<*>)?.any { identifiee(it) } == true
    }
    val entrees = (json as? Map<*, *>)?.let { m -> (m["annuaires"] as? List<*>) ?: listOf(m) }.orEmpty()
    if (entrees.none { identifiee(it) }) {
        throw GradleException("asl.annuaire.liste : « $liste » ne contient aucune entrée identifiée (`annuaire` n-… et `locateurs` littéraux) — il n'y aurait rien à joindre")
    }
    groovy.json.JsonOutput.toJson(json)
}
/** Une chaîne Java littérale : les guillemets et les barres obliques inverses échappés, les fins de ligne écrites `\n`. */
fun litteral(texte: String): String =
    "\"" + texte.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
android.defaultConfig.buildConfigField("String", "ANNUAIRES", litteral(annuaireDeTest))

dependencies {
    implementation(project(":coeur-identite"))
    implementation(project(":coeur-reseau"))
    implementation(project(":coeur-modele"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
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
    implementation(libs.unifiedpush.connector)


    testImplementation(libs.junit)
}
