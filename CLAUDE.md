# Consignes — air-service-locator-android

Tu travailles sur l'**application Android** d'air-service-locator. Ce fichier te
dit la mission, l'état réel, la première tâche, et les règles qui ne se négocient
pas. Lis-le en entier avant de toucher au code.

## Ce que l'application est

Le client mobile d'un **annuaire fédéré de daemons** : un utilisateur ouvre un
compte, déclare ses machines (des Linux), et voit quels services y écoutent et
sur quel port. L'application ADMINISTRE le compte ; ce sont les machines qui
annoncent, pas elle.

Le serveur et le protocole vivent dans le dépôt **air-service-locator-server**
(public, `github.com/air-desktop-project/air-service-locator-server`). Sa
`docs/protocole.md` est la source de vérité du fil ; `docs/modele.md` du modèle.
Si tu as besoin du détail, clone-le à côté et lis-le — ne devine pas le
protocole.

## L'état réel, sans fard

Le dépôt porte une **arborescence** Gradle (`app`, `coeur-identite`,
`coeur-modele`, `coeur-reseau`) et sa CI. Il a été posé depuis une machine
Linux ; la construction complète sur un vrai environnement Android reste à
passer. Tu es sur un Mac (oxygene) avec Android Studio : **fais d'abord compiler
et tourner les tests**, corrige ce qui bloque, avant d'ajouter du code.

## Ta première tâche, concrète

**Faire compiler et produire un jeton Play Integrity réel**, dans cet ordre :

1. Ouvrir le projet dans Android Studio, le faire **compiler** et passer ses
   tests (`./gradlew build`).
2. Intégrer [`outils-capture/CaptureIntegrity.kt`](outils-capture/CaptureIntegrity.kt) :
   il pose un défi, demande un jeton d'intégrité (API classique), et l'imprime
   dans Logcat. Il exige la dépendance `com.google.android.play:integrity` et un
   projet Google Cloud (voir le mode d'emploi).
3. Lancer sur un **appareil réel** avec les services Google Play, **récupérer le
   bloc imprimé** et les deux clés de chiffrement de réponse de la Play Console,
   et les rendre à Thierry. Ce jeton débloque la vérification côté serveur :
   `asl-play` est écrit d'après la documentation de Google et n'a jamais vu de
   vrai jeton ; cette capture confirme (ou corrige) nos hypothèses et révèle la
   forme du verdict.

Le mode d'emploi complet — projet Cloud, clés à télécharger, ce qu'il faut
noter — est dans le serveur : `docs/attestation/capture-play.md`.

## Le protocole, l'essentiel que l'app devra tenir

- **La clé de l'appareil est P-256, dans le matériel sécurisé** (StrongBox /
  Keystore adossé au TEE), sous contrôle biométrique
  (`setUserAuthenticationRequired(true)`). Ed25519 est réservé aux machines ; le
  matériel ne fait que P-256.
- **Ouvrir un compte** : `GET /v1/defi` rend un défi (32 octets) ; l'appareil
  signe une preuve de possession, et poste `POST /v1/comptes` dont le corps est
  `plateforme (1) ‖ clé (33, SEC1 compressé) ‖ preuve (64, r‖s) ‖ attestation`.
  La plate-forme vaut 2 pour Google. Le nonce du jeton Play Integrity lie la clé
  et la connexion (voir `docs/protocole.md` §2.1).
- **Aucun mot de passe** : un compte est un jeu d'appareils enrôlés. La biométrie
  est une condition d'usage de la clé, appliquée par le matériel — jamais une
  donnée envoyée.
- **Aucune donnée personnelle** hébergée, hormis un alias public facultatif.

N'écris PAS les écrans tant que le modèle n'est pas arrêté : des vues sur des
données supposées sont des vues à jeter. Concentre-toi sur le noyau (identité,
clé, réseau) et la capture.

## Les règles qui ne se négocient pas

- **Ce dépôt est PUBLIC.** Aucun secret dans un commit, un message, un fichier :
  ni clé privée, ni jeton, ni CLÉ DE CHIFFREMENT de la Play Console. Ces clés
  vont dans les réglages du serveur, jamais dans le code ni l'historique.
- **Commits** : en français, *conventional commits*, **signés GPG** (clé
  `C99EBB9BA26773011F924C4CA9F56C4D9F59EE03`), avec
  `Signed-off-by: Thierry DELHAISE <thierry.delhaise@gmail.com>`. **Aucune
  mention d'Anthropic, de Claude, ni de `Co-Authored-By`**, nulle part.
- **Ne commits et ne pushes que si Thierry le demande.** Sur la branche par
  défaut, branche d'abord.
- **Après chaque push, lis la CI** (`gh run watch`), et rapporte ce qu'elle dit.
- Le style du dépôt est exigeant et EXPLIQUÉ : le code dit pourquoi, pas
  seulement quoi. Lis un fichier existant avant d'en écrire un, et tiens le même
  registre.
