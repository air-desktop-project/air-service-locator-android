// Composition du projet air-service-locator-android.
//
// # QUATRE MODULES, ET LA RAISON DU DÉCOUPAGE
//
// Ce n'est pas de la symétrie avec le dépôt serveur : c'est la même contrainte,
// qui produit la même forme. `coeur-identite` touche au matériel sécurisé de
// l'appareil, et c'est le module dont une faute coûte le plus cher. L'isoler du
// module qui parle réseau et de celui qui dessine des écrans permet de le
// relire seul, et de lui donner ses propres essais.
//
// `coeur-modele` ne dépend de RIEN — ni d'Android, ni du réseau. C'est un module
// Kotlin pur, éprouvable sur la JVM sans émulateur.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // AUCUN dépôt déclaré dans un module ne sera accepté. Sans cela, un module
    // peut ajouter sa propre source d'artefacts sans que personne ne le voie, et
    // c'est ainsi qu'une dépendance entre par une porte qu'on n'a pas ouverte.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "air-service-locator"

include(":app")
include(":coeur-identite")
include(":coeur-reseau")
include(":coeur-modele")
