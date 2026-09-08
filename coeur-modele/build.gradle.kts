plugins {
    alias(libs.plugins.kotlin.jvm)
}

// ── UN MODULE KOTLIN PUR, ET C'EST LE POINT ─────────────────────────────────
//
// Pas de greffon Android : ce module ne connaît ni `Context`, ni `Activity`, ni
// ressources. Il porte les types que l'application manipule — utilisateur,
// machine, service, bail — et rien d'autre.
//
// Le bénéfice est concret : ses essais tournent sur la JVM, en quelques
// secondes, sans émulateur ni appareil. C'est la même frontière que celle des
// étages 1 et 2 du dépôt serveur, appliquée ici.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(libs.junit)
}
