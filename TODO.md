# TODO — ce que le serveur attend de l'app Android

Ce fichier dit **précisément** ce que la vérification côté serveur attend de
toi, pour que tu puisses le produire sans aller-retour. Il y avait deux
livrables ; le premier est rendu, le second a changé de nature le 2026-09-16 :
**Play Integrity est abandonné** (le serveur n'appelle aucun tiers, C19), et
c'est l'attestation de clé du Keystore qui le remplace — sans compte, sans
console, sans SDK.

Tout le reste de l'app (écrans, modèle, transport) est décrit dans `CLAUDE.md`,
« L'état réel, sans fard » — ce fichier ne parle que de l'attestation.

## Livrable 1 — l'app compile et les tests passent — FAIT

- La CI construit `assembleDebug` et lance les essais JVM : verte (le greffon
  Compose exigé par Kotlin 2.0, `9b2931e`).
- Construite sur oxygen avec le SDK Android, et lancée sur un **Fairphone 5**
  (Android 15, services Google Play, empreinte enrôlée). Vérifiée de bout en
  bout contre `asl-server`, puis contre `nitrogen`.

## Livrable 2 — une attestation de clé Android réelle — À FAIRE

**Pourquoi le changement.** Le jeton Play Integrity capturé le 2026-09-12 était
chiffré sous les clés de Google ; le déchiffrer demandait un compte Google Play,
l'app dans la Play Console et des clés « gérées par moi ». air-desktop ne
dépend ni de Google ni d'Apple pour fonctionner : la voie est abandonnée
(dépôt serveur, `docs/contraintes.md` C19, `docs/protocole.md` §2.1 « Décidé
le 2026-09-16 »). L'attestation de clé d'Android fait mieux — elle atteste la
clé elle-même, dans le TEE, le démarrage vérifié et l'app qui la détient —,
et se vérifie hors ligne contre une racine que l'exploitant épingle.

### Ce qu'il y a à faire, côté app

1. **La capture réelle d'abord**, sur le Fairphone 5, selon
   `docs/attestation/capture-keystore.md` du dépôt serveur : une clé jetable
   générée avec `setAttestationChallenge`, sa chaîne de certificats lue par
   `KeyStore.getCertificateChain`, imprimée en base64 dans Logcat (étiquette
   « CAPTURE »), avec le défi, le paquet, et l'empreinte SHA-256 de la
   signature de la build (`apksigner verify --print-certs`). Rien n'est un
   secret. C'est elle qui fixe la politique d'`asl-keystore` côté serveur.
2. **Retirer Play Integrity** : la dépendance
   `com.google.android.play:integrity`, `ActiviteCapture` et
   `outils-capture/CaptureIntegrity.kt`, `NUMERO_PROJET_CLOUD` dans
   `BuildConfig` et `local.properties`.
3. **La clé d'appareil attestée** (`CleAppareil.kt`) : à la génération,
   `setAttestationChallenge(SHA-256(message_d_attestation(clé, défi,
   liaison)))` — ce qui demande le défi AVANT la clé : `GET /v1/defi`, puis la
   génération, puis `POST /v1/comptes` en plate-forme `2` avec la chaîne
   (feuille d'abord, chaque DER précédé de sa longueur sur deux octets
   grand-boutiens, racine omissible). `AnnuaireReel.creerCompte` l'envoie ;
   `Appareil.Attestation` gagne `ANDROID` et `INVITATION`, `GOOGLE` disparaît.
   À brancher quand le serveur sert la plate-forme `2` (chantier
   `asl-keystore`, déposé pour speedy dans le `CLAUDE.md` du dépôt client).

### Ce que le serveur en fera

`cargo run --example verifier-une-chaine -- capture/` : la chaîne remonte-t-elle
à la racine de Google, l'extension `1.3.6.1.4.1.11129.2.1.17` porte-t-elle le
défi, un niveau de sécurité matériel, `verifiedBootState` à `Verified`, et notre
paquet sous notre empreinte ? La capture dit la forme exacte ; le code s'y plie.

## À noter

- Le `DEFI` d'une capture peut être un aléa quelconque (on valide la forme). En
  production, le défi d'attestation est
  `SHA-256(asl_cle::message_d_attestation(clé, défi, liaison))`, posé à la
  génération de la clé — la clé attestée est la clé enrôlée.

## Les règles

Commite chaque correctif (français, signé GPG, `Signed-off-by`, aucune mention
d'outil) et lis la CI après push. Le détail est dans `CLAUDE.md`.
