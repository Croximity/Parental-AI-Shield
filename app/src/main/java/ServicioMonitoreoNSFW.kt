package com.example.aplicaciondetesis

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.Executors

class ServicioMonitoreoNSFW : AccessibilityService() {

    private var analizadorVisual: AnalizadorVisualNSFW? = null
    // --- NUEVO: EL ANALIZADOR DE TEXTOS ---
    private var analizadorTexto: AnalizadorTextoRiesgoso? = null

    private var aplicacionActual: String = "Desconocida"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var estaMonitoreando = false

    private var tiempoInicioIncognito: Long = 0L
    private var alertaIncognitoEnviada = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            analizadorVisual = AnalizadorVisualNSFW(this)
            analizadorTexto = AnalizadorTextoRiesgoso(this) // Inicializamos el cerebro NLP
            estaMonitoreando = true
            iniciarBucleDeMonitoreo()
        } catch (e: Exception) {
            Log.e("MONITOREO", "Error cargando IAs: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let {
                aplicacionActual = it.toString()
            }
        }
    }

    private fun iniciarBucleDeMonitoreo() {
        scope.launch {
            while (estaMonitoreando) {
                delay(3000)

                // --- MAGIA ANTI-BUCLE: Consultamos la pantalla en tiempo real ---
                // rootInActiveWindow nos da el árbol exacto de lo que el usuario está viendo AHORA
                val nodoRaiz = rootInActiveWindow
                val paqueteEnPantalla = nodoRaiz?.packageName?.toString()
                nodoRaiz?.recycle() // Liberamos memoria de inmediato para no alentar el celular

                // Actualizamos la variable global para que los correos lleven el nombre correcto y exacto
                if (paqueteEnPantalla != null) {
                    aplicacionActual = paqueteEnPantalla
                }

                // El Escudo de Titanio: Si el paquete es nuestra app, abortamos la misión
                if (aplicacionActual == packageName) {
                    Log.d("MONITOREO", "Escudo activo: Ignorando nuestra app ($packageName)")
                    continue // Salta al siguiente ciclo de 3 segundos sin escanear nada
                }

                // 1. Verificamos modo incógnito y leemos el texto
                escanearNodosDePantalla()

                // 2. Tomamos la captura visual
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    tomarCapturaSilenciosa()
                }
            }
        }
    }    // --- NUEVO: LECTURA SIMULTÁNEA DE INCÓGNITO Y TEXTO ---
    // --- NUEVO: LECTURA SIMULTÁNEA DE INCÓGNITO Y TEXTO ---
    private fun escanearNodosDePantalla() {
        val rootNode = rootInActiveWindow ?: return

        // Verificación de incógnito (el que ya teníamos)
        val esIncognito = buscarTextoIncognito(rootNode)
        if (esIncognito) {
            if (tiempoInicioIncognito == 0L) {
                tiempoInicioIncognito = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - tiempoInicioIncognito >= 120000L && !alertaIncognitoEnviada) {
                analizadorVisual?.procesarAlertaIncognito(aplicacionActual)
                alertaIncognitoEnviada = true
            }
        } else {
            tiempoInicioIncognito = 0L
            alertaIncognitoEnviada = false
        }

        // --- MAGIA NLP: EXTRAER TODO EL TEXTO Y ENVIARLO A LA IA ---
        val textoEnPantalla = extraerTextoCompleto(rootNode)

// Dentro de tu función escanearNodosDePantalla()
        if (textoEnPantalla.isNotBlank()) {
            analizadorTexto?.procesarTextoAutomatico(textoEnPantalla, aplicacionActual) { motivo, porcentaje, textoLimpio ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Si hay peligro, mandamos a tomar la foto especial para textos
                    tomarCapturaYGuardarTexto(motivo, porcentaje, textoLimpio, aplicacionActual)
                } else {
                    // Si el celular es muy viejo y no soporta foto, guardamos solo el texto
                    analizadorTexto?.registrarAlertaConImagen(motivo, porcentaje, textoLimpio, aplicacionActual, "")
                }
            }
        }

        rootNode.recycle()
    }
    // Recolector de texto (Junta todas las palabras que hay en pantalla)
    private fun extraerTextoCompleto(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()

        if (node.text != null) sb.append(node.text).append(" ")
        if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")

        for (i in 0 until node.childCount) {
            sb.append(extraerTextoCompleto(node.getChild(i)))
        }
        return sb.toString()
    }

    private fun buscarTextoIncognito(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val texto = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val descripcion = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""

        if (texto.contains("incógnito") || texto.contains("incognito") ||
            texto.contains("inprivate") || texto.contains("navegación privada") ||
            descripcion.contains("incógnito") || descripcion.contains("incognito") ||
            descripcion.contains("inprivate") || descripcion.contains("navegación privada")) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (buscarTextoIncognito(node.getChild(i))) return true
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tomarCapturaSilenciosa() {
        val ejecutor = Executors.newSingleThreadExecutor()
        takeScreenshot(Display.DEFAULT_DISPLAY, ejecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                val buffer = screenshotResult.hardwareBuffer
                val colorSpace = screenshotResult.colorSpace
                try {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap?.recycle()
                    buffer.close()

                    softwareBitmap?.let { bmp ->
                        analizadorVisual?.procesarCapturaAutomatica(bmp, aplicacionActual)
                    }
                } catch (e: Exception) { buffer.close() }
            }
            override fun onFailure(errorCode: Int) {}
        })
    }
    override fun onInterrupt() {
        // Android exige que esta función exista, aunque la dejemos vacía
        Log.d("MONITOREO", "Servicio interrumpido por el sistema")
    }
    override fun onDestroy() {
        super.onDestroy()
        estaMonitoreando = false
        scope.cancel()
        analizadorVisual?.cerrar()
        analizadorTexto?.cerrar()
    }
    @RequiresApi(Build.VERSION_CODES.R)
    private fun tomarCapturaYGuardarTexto(motivo: String, porcentaje: Float, textoLimpio: String, appOrigen: String) {
        val ejecutor = Executors.newSingleThreadExecutor()
        takeScreenshot(Display.DEFAULT_DISPLAY, ejecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                val buffer = screenshotResult.hardwareBuffer
                val colorSpace = screenshotResult.colorSpace
                try {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap?.recycle()
                    buffer.close()

                    if (softwareBitmap != null) {
                        // Guardamos la imagen como JPG en el almacenamiento del celular
                        val rutaImg = guardarImagenTextoEnMemoria(softwareBitmap)
                        // Le devolvemos la ruta física al analizador para que la guarde en la Base de Datos
                        analizadorTexto?.registrarAlertaConImagen(motivo, porcentaje, textoLimpio, appOrigen, rutaImg)
                    } else {
                        analizadorTexto?.registrarAlertaConImagen(motivo, porcentaje, textoLimpio, appOrigen, "")
                    }
                } catch (e: Exception) {
                    buffer.close()
                    analizadorTexto?.registrarAlertaConImagen(motivo, porcentaje, textoLimpio, appOrigen, "")
                }
            }
            override fun onFailure(errorCode: Int) {
                analizadorTexto?.registrarAlertaConImagen(motivo, porcentaje, textoLimpio, appOrigen, "")
            }
        })
    }

    private fun guardarImagenTextoEnMemoria(bitmap: Bitmap): String {
        val directorio = java.io.File(applicationContext.filesDir, "EvidenciasNSFW")
        if (!directorio.exists()) directorio.mkdirs()

        // Creamos un archivo único basado en la hora actual
        val archivo = java.io.File(directorio, "alerta_texto_${System.currentTimeMillis()}.jpg")
        var fos: java.io.FileOutputStream? = null
        try {
            fos = java.io.FileOutputStream(archivo)
            // Comprimimos la imagen al 60% para que no llene la memoria del celular
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, fos)
            return archivo.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fos?.close()
        }
        return ""
    }
}