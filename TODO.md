# TODO — ce que le serveur attend de l'app Android

Ce fichier dit **précisément** ce que la vérification côté serveur attend de
toi, pour que tu puisses le produire sans aller-retour. Deux livrables, dans cet
ordre : faire compiler, puis rendre UN jeton Play Integrity réel (plus deux clés
que seul Thierry peut télécharger).

Tout le reste de l'app (écrans, modèle) n'est PAS demandé ici — n'y touche pas.

## Livrable 1 — l'app compile et les tests passent

1. Ouvrir le projet dans Android Studio.
2. `./gradlew build` doit réussir. Corrige ce qui bloque (le dépôt a été posé
   depuis Linux ; la construction complète sur un vrai environnement Android
   reste à passer).
3. Vérifie que la CI est verte après push.

Commite chaque correctif (français, signé GPG, `Signed-off-by`, aucune mention
d'outil) et lis la CI après push.

## Livrable 2 — un jeton Play Integrity réel

Le but : `asl-play`, côté serveur, sait déchiffrer et vérifier un jeton, mais
n'en a JAMAIS vu de vrai. Ce jeton confirme (ou corrige) nos hypothèses
cryptographiques ET révèle la forme du verdict, ce qui me permet d'écrire la
politique. Le mode d'emploi détaillé est dans le dépôt serveur,
`docs/attestation/capture-play.md`.

### Ce que TOI (la session Claude) peux faire

1. Ajouter la dépendance `com.google.android.play:integrity:1.4.0`.
2. Intégrer `outils-capture/CaptureIntegrity.kt`, remplir `NUMERO_PROJET_CLOUD`
   (le NUMÉRO du projet Google Cloud), appeler `capturerUnJeton(context)` une
   fois.
3. Lancer sur un **appareil réel avec les services Google Play**, lire Logcat
   (étiquette « CAPTURE »), récupérer le bloc :

```
JETON=<le jeton, tel quel — c'est déjà du texte>
DEFI=<le nonce que l'app a posé>
PAQUET=<le nom du paquet>
```

### Ce que THIERRY doit faire (tu ne peux pas — c'est la Play Console)

- Créer/associer un projet Google Cloud, activer l'**API Play Integrity**.
- Dans la **Google Play Console → App integrity → Response encryption**, choisir
  « **Manage and download my response encryption keys** » et télécharger :
  - la **clé de déchiffrement** (AES-256, base64) ;
  - la **clé de vérification** (clé publique EC, SPKI, base64).

**Ces deux clés sont des secrets d'exploitation** : elles NE vont NI dans un
commit, NI dans ce dépôt public, NI dans Logcat. Thierry me les transmet par un
canal privé, et elles finiront dans les réglages du serveur.

### Ce que le serveur en fera

Écrire `JETON` dans un fichier, décoder les deux clés en fichiers, puis :
`cargo run --example verifier-un-jeton -- capture/`. S'il dit **✔ OUVERT**, il
imprime le **verdict JSON réel** — et c'est lui qui me dit quels champs lire et
quelles valeurs accepter, pour écrire la politique et brancher
`PlateformeAttestation::Google` (aujourd'hui refusée). Sinon, il dira laquelle
de nos hypothèses (`A256KW`, `A256GCM`, ES256) corriger.

## À noter

- Sans les clés « gérées par moi » de la Play Console, le jeton ne se déchiffre
  PAS hors ligne — il faudrait appeler Google, ce que l'annuaire ne fait pas.
  Les deux clés sont donc indispensables au livrable 2.
- Le `DEFI` d'une capture peut être un aléa quelconque (on valide la forme). En
  production, le nonce portera notre liaison :
  `base64url(asl_cle::message_d_attestation(clé, défi, liaison))`.
