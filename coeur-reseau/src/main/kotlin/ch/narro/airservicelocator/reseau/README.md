# coeur-reseau

Le client de l'API d'`air-service-locator`.

**Vide.** L'API n'est pas spécifiée — elle l'est dans le dépôt serveur,
`docs/protocole.md`, section 2, qui n'en consigne pour l'instant que les
questions ouvertes. Aucune bibliothèque HTTP n'est donc encore tirée : le
transport lui-même n'est pas choisi.

La règle qui gouvernera ce module : **rien ne part d'ici qui ne soit signé par
une clé du Keystore matériel** (cf. module `coeur-identite`).
