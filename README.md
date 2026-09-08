# air-service-locator-android

L'application Android d'**air-service-locator** : ouvrir un compte, déclarer ses
machines, et voir quels daemons y écoutent — et sur quel port.

> ## État : une arborescence, et un seul type qui fait quelque chose
>
> Le dépôt porte sa structure, sa composition Gradle et sa CI. Il ne contient
> **aucun écran** au-delà d'un provisoire qui dit où en est le projet : les
> spécifications ne sont pas écrites, et dessiner des vues avant que le modèle
> soit arrêté produirait des écrans qui décrivent des données supposées.
>
> **RIEN ICI N'A ÉTÉ CONSTRUIT.** Le dépôt a été posé depuis une machine Linux
> sans JDK ni SDK Android. Le couple AGP 8.5.2 / Gradle 8.7 est celui que l'amont
> documente comme compatible, et le wrapper vient d'un projet qui tourne — mais
> la première construction reste un contrôle à passer.

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
| `app` | L'activité et les écrans. | oui |
| `coeur-identite` | Ce que l'appareil sait confirmer, et la clé matérielle à venir. | oui |
| `coeur-reseau` | Le client de l'API. Vide — le transport n'est pas choisi. | oui |
| `coeur-modele` | Utilisateur, machine, service, bail. **Kotlin pur.** | non |

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

## Ce que ce dépôt ne contient pas, et où c'est

| Question | Où elle est traitée |
|---|---|
| Utilisateurs, machines, services, baux | `air-service-locator-server`, `docs/modele.md` |
| Ce que l'application envoie et reçoit | `air-service-locator-server`, `docs/protocole.md` §2 |
| Ce que le serveur constate d'une identité | `air-service-locator-server`, `crates/asl-auth` |

## Licence

MPL-2.0 — voir [LICENSE](LICENSE).
