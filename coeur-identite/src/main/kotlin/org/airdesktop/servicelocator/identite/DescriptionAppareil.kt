package org.airdesktop.servicelocator.identite

import android.os.Build
import org.airdesktop.servicelocator.modele.Appareil

/**
 * Ce que CET appareil dit de lui-même à l'annuaire
 * (`PUT /v1/appareils/{a}/description`, `docs/protocole.md` §2.2).
 *
 * # Le modèle, et jamais le nom
 *
 * Ce qui part est le **modèle** que le système rend — `Build.MANUFACTURER` et
 * `Build.MODEL`, « Fairphone FP5 » : une désignation d'usine, qui ne nomme
 * personne. Ce qui ne part PAS est le nom que l'utilisateur a donné au
 * téléphone (`Settings.Global.DEVICE_NAME`, le nom Bluetooth) : il porte
 * souvent un prénom, précisément ce que C13 refuse. L'écran de l'appareil
 * lui-même peut montrer ce nom localement — il ne quitte pas l'appareil.
 *
 * Ce fichier vit ici, et non dans le modèle, parce qu'il lit `Build` : le
 * modèle ne connaît pas Android, et ses essais tournent sur la JVM.
 */
fun Appareil.Description.Companion.deCetAppareil(): Appareil.Description {
    val fabricant = Build.MANUFACTURER.orEmpty().trim()
    val modele = Build.MODEL.orEmpty().trim()
    // Certains fabricants se répètent dans le modèle (« Fairphone Fairphone
    // FP5 » n'aide personne) ; on ne préfixe que si le modèle ne le dit pas déjà.
    val texte = when {
        modele.isEmpty() -> fabricant.ifEmpty { "Android" }
        fabricant.isEmpty() || modele.startsWith(fabricant, ignoreCase = true) -> modele
        else -> "$fabricant $modele"
    }
    return Appareil.Description(Appareil.Plateforme.ANDROID, borner(texte))
}

/**
 * Un à soixante-quatre octets, coupés sur une frontière de caractère : les
 * règles du nom de machine, tenues avant d'envoyer plutôt que refusées par
 * l'annuaire.
 */
private fun borner(modele: String): String {
    var texte = modele
    while (texte.toByteArray(Charsets.UTF_8).size > Appareil.Description.MODELE_OCTETS_MAX) texte = texte.dropLast(1)
    return texte.ifEmpty { "?" }
}
