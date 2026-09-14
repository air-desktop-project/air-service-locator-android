# TODO — ce que le serveur attend de l'app Android

Ce fichier dit **précisément** ce que la vérification côté serveur attend de
toi, pour que tu puisses le produire sans aller-retour. Il y avait deux
livrables ; le premier est rendu, le second est à moitié rendu et attend la
Play Console.

Tout le reste de l'app (écrans, modèle, transport) est décrit dans `CLAUDE.md`,
« L'état réel, sans fard » — ce fichier ne parle que de l'attestation.

## Livrable 1 — l'app compile et les tests passent — FAIT

- La CI construit `assembleDebug` et lance les essais JVM : verte (le greffon
  Compose exigé par Kotlin 2.0, `9b2931e`).
- Construite sur oxygen avec le SDK Android, et lancée sur un **Fairphone 5**
  (Android 15, services Google Play, empreinte enrôlée). Vérifiée de bout en
  bout contre `asl-server`, puis contre `nitrogen`.

## Livrable 2 — un jeton Play Integrity réel — À MOITIÉ

Le but : `asl-play`, côté serveur, sait déchiffrer et vérifier un jeton, mais
n'en avait JAMAIS vu de vrai. Le mode d'emploi détaillé est dans le dépôt
serveur, `docs/attestation/capture-play.md`.

### Ce qui est fait (côté app)

- La dépendance `com.google.android.play:integrity:1.4.0`, en
  `debugImplementation` seulement (`d934984`).
- `outils-capture/CaptureIntegrity.kt` est branché dans la variante de
  débogage (`app/src/debug/.../capture/ActiviteCapture.kt`, un bouton). Le
  numéro de projet Google Cloud vient de `local.properties` (hors dépôt) et
  entre dans `BuildConfig.NUMERO_PROJET_CLOUD`.
- **Un premier jeton réel a été capturé** sur le Fairphone le 2026-09-12. Il
  est dans le dépôt serveur, branche `capture-play-integrity`,
  `docs/attestation/captures/play-integrity-2026-09-12.txt`. Il **confirme la
  grammaire** : JWE `A256KW`/`A256GCM` → JWS, vérifié octet pour octet
  (5 segments, CEK 40, IV 12, tag 16).

### Ce qui manque, et pourquoi

Le jeton capturé est chiffré sous **les clés que Google gère** : il ne se
déchiffre pas hors ligne, et l'annuaire n'appelle pas Google. Le **verdict**
reste donc inconnu, et sans verdict pas de politique dans `asl-play` —
`PlateformeAttestation::Google` reste refusée par le serveur.

### Ce que THIERRY doit faire (tu ne peux pas — c'est la Play Console)

- Le projet Google Cloud existe et l'API Play Integrity est activée (numéro
  `861147308432`). **L'app n'est pas encore dans la Play Console.**
- L'y inscrire, puis dans **Google Play Console → App integrity → Response
  encryption**, choisir « **Manage and download my response encryption keys** »
  et télécharger :
  - la **clé de déchiffrement** (AES-256, base64) ;
  - la **clé de vérification** (clé publique EC, SPKI, base64).

**Ces deux clés sont des secrets d'exploitation** : elles NE vont NI dans un
commit, NI dans ce dépôt public, NI dans Logcat. Thierry les transmet par un
canal privé, et elles finiront dans les réglages du serveur.

### Ce que TOI (la session Claude) feras ensuite

Recapturer UN jeton — les clés « gérées par moi » changent ce sous quoi Google
chiffre, donc le jeton du 2026-09-12 ne servira pas. Même geste : le bouton de
`ActiviteCapture` sur le Fairphone, Logcat (étiquette « CAPTURE »), le bloc :

```
JETON=<le jeton, tel quel — c'est déjà du texte>
DEFI=<le nonce que l'app a posé>
PAQUET=<le nom du paquet>
```

### Ce que le serveur en fera

Écrire `JETON` dans un fichier, décoder les deux clés en fichiers, puis :
`cargo run --example verifier-un-jeton -- capture/`. S'il dit **✔ OUVERT**, il
imprime le **verdict JSON réel** — et c'est lui qui dit quels champs lire et
quelles valeurs accepter, pour écrire la politique et brancher
`PlateformeAttestation::Google`. Sinon, il dira laquelle de nos hypothèses
(`A256KW`, `A256GCM`, ES256) corriger.

## À noter

- Le `DEFI` d'une capture peut être un aléa quelconque (on valide la forme). En
  production, le nonce portera notre liaison :
  `base64url(asl_cle::message_d_attestation(clé, défi, liaison))`.

## Les règles

Commite chaque correctif (français, signé GPG, `Signed-off-by`, aucune mention
d'outil) et lis la CI après push. Le détail est dans `CLAUDE.md`.
