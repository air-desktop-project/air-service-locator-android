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

Les **onze écrans sont écrits** (Compose, Material 3, minSdk 28) et **parlent
au vrai annuaire** par le transport d'`asl-client` (`coeur-reseau`, `reel/`),
ou au banc en mémoire (`AnnuaireSimule`) quand aucun annuaire n'est configuré
(`README.md`, « Parler à un vrai annuaire »). Tout compile sans avertissement
et les essais JVM passent (`./gradlew assembleDebug test`). Les maquettes
validées sont dans `../maquettes/` (hors dépôt). Vérifié de bout en bout sur le
Fairphone contre `asl-server` : compte, machine, enrôlement, annonce, service
joignable.

Ce qui manque, dans l'ordre où ça se fera :

1. ~~La clé P-256 dans le Keystore~~ — **faite** (`coeur-identite/CleAppareil.kt`,
   `coeur-modele/P256.kt`) : StrongBox ou TEE, biométrie forte à chaque
   signature, `r ‖ s` et SEC1 compressé dépliés en Kotlin pur. Vérifiée sur le
   Fairphone 5.
2. ~~Le transport~~ — **fait** (`reel/AnnuaireReel.kt`, `reel/Natif.kt`) : les
   symboles JNI de la crate `asl-client-android`, la signature par rappel (le
   natif rappelle depuis son fil ; `BiometricPrompt` s'affiche sur le fil
   principal, dans l'activité au premier plan *au moment de signer*), la
   connexion tenue par `ApplicationServiceLocator` — pas par l'activité, qu'une
   rotation recrée. Ce que le serveur ne rend pas encore vient d'un `Carnet`
   local (SharedPreferences) et l'écran le dit : les manques sont listés dans
   le `CLAUDE.md` du dépôt client, à l'attention de speedy.
3. **La capture Play Integrity** (`outils-capture/`), qui exige un projet
   Google Cloud (fait, le numéro est dans `local.properties`) et l'app dans la
   Play Console (à faire).
4. ~~Les écrans restants~~ — **faits** : le détail d'un service
   (`ServiceEcran.kt`, verdict par point, candidats, ce que l'annuaire a
   répondu au daemon), et le second appareil par échange de QR codes
   (`coeur-modele/Invitation.kt`, `AppareilsEcrans.kt` : enrôler côté ancien,
   rejoindre côté nouveau ; `composants/CodeQR.kt` trace avec ZXing et lit
   avec CameraX). Les expositions restent un libellé tant que le serveur rend
   `501`.
5. Un état de chargement au lancement : l'accueil apparaît un instant avant
   que le compte soit relu. Et le nom d'un service, l'état d'une clé, la liste
   des appareils enrôlés ailleurs : dès que le serveur les rend.

Tu es sur un Mac (oxygen) avec le SDK Android ; un **Fairphone 5** est branché
en USB (`adb devices`), avec une empreinte enrôlée.

## Ce qu'il faut tenir en écrivant un écran

- **Les écrans parlent à `Annuaire`, jamais au banc ni au transport.**
  `AnnuaireSimule` et `AnnuaireReel` ne sont nommés que dans la composition
  (`ApplicationServiceLocator`) et les essais.
- **Le vocabulaire est celui de `modele.md` §4.2** : `annoncé`, `joignable`
  (avec sa date), `parti (volontaire / inactivité)`, UDP `non sondé`. Le mot
  « en ligne » n'apparaît nulle part.
- **Un identifiant se compare sur ses octets** (`Identifiant`), jamais comme
  une chaîne ; il ne s'affiche que par `texte` ou `abrege`, et voyage dans une
  route de navigation sous sa forme canonique.
- **Ce que l'on ne sait pas faire se dit à l'écran**, on ne le simule pas.
- Les dates s'affichent en français quel que soit le réglage du téléphone
  (`Formats.relatif`, `Formats.jour`).
- Les icônes sont tracées dans `Icones.kt`, pas tirées de
  `material-icons-extended`.

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
