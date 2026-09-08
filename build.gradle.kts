// Racine du projet.
//
// Les greffons sont déclarés ici avec `apply false` : la racine ne construit
// rien elle-même, elle fixe LES VERSIONS pour tous les modules. Un module qui
// déclarerait sa propre version d'AGP ou de Kotlin ferait deux vérités sur ce
// qui compile le projet.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
