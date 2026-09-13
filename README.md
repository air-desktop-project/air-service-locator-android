# air-service-locator-android

L'application Android d'**air-service-locator** : ouvrir un compte, déclarer ses
machines, et voir quels daemons y écoutent — et sur quel port.

> ## État : onze écrans, sur le vrai annuaire
>
> L'application compile (AGP 8.5.2, Kotlin 2.0, avertissements en erreurs) et
> tourne sur un Fairphone 5. Elle porte les huit écrans arrêtés avec les
> maquettes — accueil, machines, machine, déclaration, code d'enrôlement,
> accès, accorder, compte —, trois écrans de plus — le détail d'un service,
> enrôler un second appareil, rejoindre un compte — et trente et un essais JVM.
>
> **Elle parle à un annuaire réel** quand on lui en donne un (voir
> « Construire ») : HTTP/3 sur QUIC, par la pile Rust d'`asl-client`
> embarquée en bibliothèque native et liée par JNI
> (`coeur-reseau`, `reel/AnnuaireReel.kt`, `reel/Natif.kt`). La connexion est
> **tenue**, au niveau du processus : un geste biométrique par connexion, pas
> par requête, et pas par rotation d'écran. Sans annuaire configuré, c'est
> `AnnuaireSimule` qui répond : un banc en mémoire qui tient les refus de
> `docs/protocole.md` §2 — un appareil ne se révoque pas lui-même, un alias
> pris rend `409`, un objet absent et un objet d'un autre compte rendent le
> même `404`. Les écrans ne voient que l'interface `Annuaire` ; c'est
> `ApplicationServiceLocator` qui choisit.
>
> **La clé de l'appareil est réelle** : P-256 dans le Keystore matériel
> (StrongBox si l'appareil en a un, sinon le TEE), biométrie forte exigée à
> chaque signature par `BiometricPrompt` et son `CryptoObject`. Ouvrir un compte
> est une vraie preuve de possession — le corps de `POST /v1/comptes` : clé
> SEC1 compressée, signature `r ‖ s` sur le défi de l'annuaire et la liaison du
> canal TLS — que le serveur vérifie. Vérifié de bout en bout sur le Fairphone
> contre un serveur `asl-server` : compte, machine, enrôlement par
> `asl enrole`, annonce, service joignable.
>
> **Le serveur fait foi** : la liste des machines, des appareils, des
> services avec leur nom et leur état viennent de lui (serveur `2cf05dc`).
> Ce qu'il ne range pas — les dates, le code d'enrôlement en cours, la trace
> d'une révocation — n'est connu que du téléphone qui a agi, et s'affiche
> quand il le sait, jamais inventé : « enrôlée » sans date vaut « enrôlée
> depuis un autre appareil ». Une chose est dite « pas encore possible » à
> l'écran plutôt que simulée : les expositions (`501` côté serveur).
>
> **Un second appareil s'enrôle par un échange de QR codes** (`POST
> /v1/appareils`, `coeur-modele/Invitation.kt`) : le nouveau montre sa clé
> publique, l'ancien la lit à la caméra — CameraX et ZXing, sans services
> Google — ou la colle, la poste, et montre en retour le compte et
> l'identifiant rendus ; le nouveau les lit et prouve sa clé sur sa propre
> connexion. Rien de secret ne passe. Vérifié entre le Fairphone et le
> simulateur iOS, dans les deux sens. C'est la seule permission que
> l'application demande en plus de la biométrie, et elle se demande au moment
> de lire.

## La condition de déploiement

**Cette application ne s'installe que sur un appareil capable de confirmer
localement l'identité de son porteur.**

Elle est appliquée à DEUX endroits, et ni l'un ni l'autre ne suffit seul :

| Où | Ce que cela filtre |
|---|---|
| `uses-feature android.hardware.biometrics required="true"` | Google Play retire l'application du catalogue des appareils sans le matériel — **avant** l'installation. |
| [`IdentiteLocale`](coeur-identite/src/main/kotlin/org/airdesktop/servicelocator/identite/IdentiteLocale.kt) | Le matériel présent mais rien d'enrôlé, ou une biométrie seulement « faible » — **après** l'installation. |

Ce que cela veut *réellement* dire mérite d'être écrit, parce que la version
courte induit en erreur :

- La confirmation a lieu **sur l'appareil**. Android ne rend jamais un gabarit
  facial ni une empreinte — ces données vivent dans le TEE, et aucune API ne les
  expose.
- Ce que le code obtient est un **verdict de disponibilité**, et ce n'est pas une
  preuve : un client modifié en renverrait un aussi.
- Ce qui vaut preuve auprès de l'annuaire est une **signature** produite par une
  clé du Keystore matériel, créée avec `setUserAuthenticationRequired(true)` : le
  système refuse de s'en servir tant que le porteur n'a pas été reconnu.

D'où l'exigence de `BIOMETRIC_STRONG` : seule la classe forte déverrouille une
clé du Keystore. La classe faible rend un booléen, et rien de plus.

## Les modules

| Module | Ce qu'il porte | Dépend d'Android ? |
|---|---|---|
| `app` | L'activité, la navigation, les écrans Compose et leurs composants. | oui |
| `coeur-identite` | Ce que l'appareil sait confirmer, le geste de confirmation, et la clé P-256 du Keystore. | oui |
| `coeur-reseau` | L'interface `Annuaire`, ses erreurs ; `reel/` — le transport QUIC d'`asl-client` par JNI et le carnet local ; le banc `AnnuaireSimule` avec ses données de démonstration. | oui |
| `coeur-modele` | Identifiant (base32 de Crockford, seize octets), code d'enrôlement, compte, appareil, machine, service, autorisation, les messages à signer, le signataire, et l'invitation qu'échangent deux téléphones. **Kotlin pur.** | non |

`coeur-modele` n'a pas de greffon Android, et c'est le point : ses essais
tournent sur la JVM en quelques secondes, sans émulateur. C'est la frontière des
étages 1 et 2 du dépôt serveur, appliquée ici.

## L'icône

Elle vient du dépôt iOS, `Outils/Icone/generer.py` : un seul dessin pour
iOS, macOS et Android. Les deux VectorDrawable de `res/drawable/`
(`ic_launcher_foreground.xml`, `ic_launcher_background.xml`) en sont la
sortie, copiée telle quelle — on la retouche là-bas, jamais ici.

## Construire

```sh
./gradlew assembleDebug
./gradlew test
```

Il faut un JDK 17 et un SDK Android (API 34). `local.properties` n'est pas
versionné : il porte le chemin du SDK sur *votre* machine.

### Parler à un vrai annuaire

Le transport est la bibliothèque native produite par le dépôt client, attendue
à `../air-service-locator-client/target/mobile/jniLibs/arm64-v8a/libasl_client_android.so`
(`scripts/construire-mobile.sh` là-bas). Sans elle, l'application s'installe
mais `System.loadLibrary` échoue au premier usage du transport réel.

L'annuaire se donne dans `local.properties` (non versionné) et passe dans
`BuildConfig` :

```
asl.annuaire.adresse=192.168.1.102:6630
asl.annuaire.nom=speedy
asl.annuaire.racines=/chemin/vers/racine.pem
```

`nom` est le nom que porte le certificat du serveur ; `racines`, le chemin
local de la racine qui l'a signé (son contenu est embarqué à la construction —
une racine publique, rien de secret). Sans ces trois lignes, l'application
tourne sur le banc en mémoire, peuplé de démonstration.

## Capturer un jeton Play Integrity

La variante de débogage embarque `outils-capture/CaptureIntegrity.kt` derrière
une activité sans lanceur. Il faut le **numéro** du projet Google Cloud, posé
dans `local.properties` (non versionné) :

```
asl.numeroProjetCloud=123456789012
```

```sh
./gradlew installDebug
adb shell am start -n org.airdesktop.servicelocator/.capture.ActiviteCapture
adb logcat -s CAPTURE
```

Le bloc `JETON=` / `DEFI=` / `PAQUET=` s'affiche à l'écran et dans Logcat. Les
deux clés de chiffrement de réponse, elles, viennent de la Play Console et ne
passent ni par ce dépôt ni par Logcat — voir `docs/attestation/capture-play.md`
du serveur.

## Ce que ce dépôt ne contient pas, et où c'est

| Question | Où elle est traitée |
|---|---|
| Utilisateurs, machines, services, baux | `air-service-locator-server`, `docs/modele.md` |
| Ce que l'application envoie et reçoit | `air-service-locator-server`, `docs/protocole.md` §2 |
| Ce que le serveur constate d'une identité | `air-service-locator-server`, `crates/asl-auth` |

## Licence

MPL-2.0 — voir [LICENSE](LICENSE).
