package org.airdesktop.servicelocator.composants

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.airdesktop.servicelocator.modele.Invitation
import java.util.concurrent.Executors

/**
 * Un QR code, tracé depuis un texte — pour qu'un autre téléphone le lise.
 *
 * ZXing rend une matrice de modules ; on la peint un module par pixel et on
 * l'agrandit **sans filtrage**, sinon les modules baveraient et la lecture
 * échouerait à la première réflexion sur l'écran.
 */
@Composable
fun CodeQR(texte: String, taille: androidx.compose.ui.unit.Dp = 220.dp) {
    val image = remember(texte) { tracer(texte) }
    if (image != null) {
        Image(
            image.asImageBitmap(), contentDescription = "Code à lire par l'autre téléphone",
            modifier = Modifier.size(taille), filterQuality = FilterQuality.None,
        )
    } else {
        Text("Le code n'a pas pu être tracé.", color = MaterialTheme.colorScheme.error)
    }
}

private fun tracer(texte: String): Bitmap? = runCatching {
    // Correction « M » : assez pour un écran lu de près, sans grossir le code
    // au point de le rendre illisible sur un petit téléphone. Marge à zéro :
    // l'écran, blanc autour, la fournit.
    val matrice = QRCodeWriter().encode(
        texte, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
    Bitmap.createBitmap(matrice.width, matrice.height, Bitmap.Config.RGB_565).apply {
        for (x in 0 until matrice.width) for (y in 0 until matrice.height) {
            setPixel(x, y, if (matrice[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}.getOrNull()

/**
 * Le lecteur de QR code : la caméra arrière, et rien d'autre. S'arrête dès
 * qu'un code a été lu — on ne filme pas plus longtemps que nécessaire.
 *
 * La permission se demande ici, au moment où l'on en a besoin, et son refus
 * n'est pas une impasse : l'écran qui emploie ce lecteur propose toujours le
 * champ texte à côté.
 */
@Composable
fun LecteurQR(surLecture: (String) -> Unit, modifier: Modifier = Modifier) {
    val contexte = LocalContext.current
    val cycleDeVie = LocalLifecycleOwner.current
    var accordee by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var refusee by remember { mutableStateOf(false) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { oui ->
        accordee = oui
        refusee = !oui
    }
    val executeur = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executeur.shutdown() } }

    Box(modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        when {
            accordee -> AndroidView(
                factory = { ctx ->
                    val vue = PreviewView(ctx)
                    val fournisseur = ProcessCameraProvider.getInstance(ctx)
                    fournisseur.addListener({
                        val camera = fournisseur.get()
                        val apercu = Preview.Builder().build().also { it.surfaceProvider = vue.surfaceProvider }
                        val analyse = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        var lu = false
                        analyse.setAnalyzer(executeur) { image ->
                            val code = if (lu) null else decoder(image)
                            image.close()
                            if (code != null && !lu) {
                                lu = true
                                // Fini de filmer : on détache tout, sur le fil principal.
                                vue.post {
                                    camera.unbindAll()
                                    surLecture(code)
                                }
                            }
                        }
                        camera.unbindAll()
                        runCatching { camera.bindToLifecycle(cycleDeVie, CameraSelector.DEFAULT_BACK_CAMERA, apercu, analyse) }
                    }, ContextCompat.getMainExecutor(ctx))
                    vue
                },
                modifier = Modifier.fillMaxSize(),
            )
            refusee -> Text(
                "Sans la caméra, collez le code dans le champ ci-dessous.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
            )
            else -> OutlinedButton(onClick = { demande.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
        }
    }
}

/** Décode un QR code dans une image YUV de CameraX : le plan de luminance suffit. */
private fun decoder(image: ImageProxy): String? {
    val plan = image.planes[0]
    val tampon = plan.buffer
    val octets = ByteArray(tampon.remaining()).also { tampon.get(it) }
    val source = PlanarYUVLuminanceSource(octets, plan.rowStride, image.height, 0, 0, image.width, image.height, false)
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
    } catch (e: NotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Recevoir une invitation : la lire à la caméra, ou la coller. Les deux mènent
 * au même endroit ; le champ n'est pas un pis-aller mais le chemin d'une
 * caméra refusée, ou d'un code envoyé par message.
 */
@Composable
fun ReceptionInvitation(attendu: String, surInvitation: (Invitation) -> Unit) {
    var texte by remember { mutableStateOf("") }
    var lecteur by remember { mutableStateOf(false) }
    var refus by remember { mutableStateOf<String?>(null) }

    fun recevoir(candidat: String) {
        val invitation = Invitation.analyser(candidat)
        if (invitation == null) {
            refus = "Ce n'est pas un code de cette application."
            return
        }
        refus = null
        surInvitation(invitation)
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        if (lecteur) {
            LecteurQR(surLecture = { lecteur = false; recevoir(it) })
            TextButton(onClick = { lecteur = false }) { Text("Fermer la caméra") }
        } else {
            OutlinedButton(onClick = { lecteur = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icones.qr, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Lire le code à la caméra")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = texte, onValueChange = { texte = it }, label = { Text("ou collez-le ici") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            // Un code, pas une phrase : le clavier n'a rien à y corriger — il
            // ferait de `cle` un `clé`. `Uri` est le type que les claviers
            // respectent vraiment sur ce point ; `autoCorrect = false` seul
            // ne suffit pas à Gboard.
            keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Uri),
            isError = refus != null, supportingText = { Text(refus ?: attendu) },
        )
        if (texte.isNotBlank()) {
            TextButton(onClick = { recevoir(texte) }) { Text("Valider") }
        }
    }
}

/** Un texte long en police fixe, avec le bouton pour le copier. */
@Composable
fun LigneCopiable(titre: String, texte: String) {
    val presse = LocalClipboardManager.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(titre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
            Text(texte, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { presse.setText(AnnotatedString(texte)) }) { Icon(Icones.copier, "Copier", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** Le blanc autour d'un QR code, quel que soit le thème : un lecteur veut du contraste. */
@Composable
fun CadreQR(texte: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(8.dp).then(Modifier.size(236.dp)), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawRect(Color.White) }
            CodeQR(texte)
        }
    }
}
