# air-service-locator-android

L'application Android d'**air-service-locator** : ouvrir un compte, déclarer ses
machines, et voir quels daemons y écoutent — et sur quel port.

> ## État : les huit écrans, sur un annuaire simulé
>
> L'application compile (AGP 8.5.2, Kotlin 2.0, avertissements en erreurs) et
> tourne sur un Fairphone 5. Elle porte les huit écrans arrêtés avec les
> maquettes — accueil, machines, machine, déclaration, code d'enrôlement,
> accès, accorder, compte — et dix-neuf essais JVM.
>
> **Elle ne parle à aucun serveur.** Les écrans s'adressent à l'interface
> `Annuaire` (`coeur-reseau`), et c'est `AnnuaireSimule` qui répond : un banc
> en mémoire qui tient les refus de `docs/protocole.md` §2 — un appareil ne se
> révoque pas lui-même, un alias pris rend `409`, un objet absent et un objet
> d'un autre compte rendent le même `404`. Le transport réel — la pile QUIC
> d'`asl-client` par JNI, l'authentification liée au canal, la clé P-256 dans
> le Keystore — reste à embarquer, et c'est la composition dans
> `ActivitePrincipale` qui changera, pas les écrans.
>
> Trois choses sont dites « pas encore possible » à l'écran plutôt que
> simulées : enrôler un second appareil, les expositions (`501` côté serveur),
> et le jeton Play Integrity, qui exige un projet Google Cloud.

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
| `coeur-identite` | Ce que l'appareil sait confirmer, le geste de confirmation, et la clé matérielle à venir. | oui |
| `coeur-reseau` | L'interface `Annuaire`, ses erreurs, et le banc `AnnuaireSimule` avec ses données de démonstration. | oui (bibliothèque), mais rien d'Android n'y est appelé |
| `coeur-modele` | Identifiant (base32 de Crockford, seize octets), code d'enrôlement, compte, appareil, machine, service, autorisation. **Kotlin pur.** | non |

`coeur-modele` n'a pas de greffon Android, et c'est le point : ses essais
tournent sur la JVM en quelques secondes, sans émulateur. C'est la frontière des
étages 1 et 2 du dépôt serveur, appliquée ici.

## Construire

```sh
./gradlew assembleDebug
./gradlew test
```

Il faut un JDK 17 et un SDK Android (API 34). `local.properties` n'est pas
versionné : il porte le chemin du SDK sur *votre* machine.

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
