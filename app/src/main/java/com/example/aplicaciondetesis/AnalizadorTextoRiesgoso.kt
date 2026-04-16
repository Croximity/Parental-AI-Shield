package com.example.aplicaciondetesis

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class AnalizadorTextoRiesgoso(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val dbHelper = BitacoraDbHelper(context)

    // Cronómetro Anti-Spam para los textos (1 minuto)
    private var tiempoUltimaAlerta: Long = 0L
    private val TIEMPO_DESCANSO_MS: Long = 60000L

    init {
        try {
            val modelBuffer = loadModelFile(context, "modelo_riesgo_infantil_v3.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            android.util.Log.e("NLP", "Error cargando modelo de texto: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun evaluarTexto(texto: String): Float {
        if (interpreter == null || texto.isBlank()) return 0.0f

        val inputs = arrayOf(texto)
        val outputBuffer = Array(1) { FloatArray(1) }

        interpreter?.run(inputs, outputBuffer)
        return outputBuffer[0][0]
    }

    // --- FUNCIÓN 1: SOLO DETECTA EL RIESGO Y AVISA (No guarda en Base de Datos) ---
    fun procesarTextoAutomatico(
        textoEscaneado: String,
        appOrigen: String,
        onAlertaDetectada: (motivo: String, porcentaje: Float, textoLimpio: String) -> Unit
    ) {
        val textoLimpio = textoEscaneado.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (textoLimpio.length < 3) return

        var textoMinusculas = textoLimpio.lowercase(java.util.Locale.getDefault())
        textoMinusculas = textoMinusculas
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")

        val palabrasProhibidas = listOf(
            // 🚫 NSFW / PORNOGRAFÍA / SEXTING
            "porno", "pornografía", "xvideos", "pornhub", "onlyfans", "redtube", "xnxx",
            "nudes", "pack", "pasa pack", "pasa fotos", "manda fotos",
            "desnuda", "desnudo", "sin ropa", "en calzones", "en bragas", "en tanga",
            "masturbar", "masturbando", "horny", "cachondo", "cachonda",
            "caliente", "excitado", "excitada", "orgasmo", "gemir", "gime", "gimiendo",
            "pene", "verga", "pito", "polla", "bicho", "mazacuata", "pinga",
            "vagina", "panocha", "cuca", "cuka", "puchaina", "concha",
            "culos", "nalgas", "tetas", "senos", "pechos", "pezones",
            "follar", "coger", "tirar", "sexo", "coito", "anal",
            "puta", "puto", "zorra", "perra", "ramera", "prostituta",

            // 🛑 GROOMING / PEDOFILIA / MANIPULACIÓN
            "es nuestro secreto", "no le digas a tus papás", "no le digas a tu mamá",
            "no le cuentes a nadie", "borra el chat", "borra esto", "borra los mensajes",
            "chat secreto", "modo efímero", "manda ubicación", "dónde vives",
            "tienes webcam", "enciende la cámara", "ponte frente a la cámara",
            "qué tienes puesto", "qué llevas puesto", "pásame tu whatsapp", "pasa tu insta",
            "eres muy madura para tu edad", "castigo o reto", "verdad o reto",
            "juego caliente", "quítate la ropa", "tócate",

            // ⚠️ CIBERACOSO / BULLYING
            "muérete", "suicídate", "mátate", "date un tiro", "cuelgate",
            "eres una basura", "no sirves para nada", "ojalá te mueras", "desaparece",
            "fenómeno", "fenómena", "aborto", "nadie te quiere",
            "das asco", "das asco", "das pena", "ridículo", "idiota", "imbécil", "retrasado",
            "gordo de mierda", "fea de mierda", "fracasado", "fracasada",
            "te voy a golpear", "te vamos a pegar", "te voy a matar", "golpiza",

            // 💊 DROGAS Y SUSTANCIAS
            "marihuana", "porro", "weed", "cocaína", "coca", "cristal", "éxtasis",
            "fentanilo", "lsd", "heroína", "drogarse", "estar trabado", "estar fumado"
        )

        var esRiesgo = false
        var porcentajeFloat = 0.0f
        var motivo = ""

        if (palabrasProhibidas.any { palabra -> Regex("\\b$palabra\\b").containsMatchIn(textoMinusculas) }) {
            esRiesgo = true
            porcentajeFloat = 100.0f
            motivo = "Filtro Directo"
        } else {
            val probabilidadRiesgo = evaluarTexto(textoLimpio)
            porcentajeFloat = probabilidadRiesgo * 100
            if (probabilidadRiesgo > 0.60f) {
                esRiesgo = true
                motivo = "IA Contextual"
            }
        }

        if (esRiesgo) {
            val tiempoActual = System.currentTimeMillis()
            if (tiempoActual - tiempoUltimaAlerta >= TIEMPO_DESCANSO_MS) {
                tiempoUltimaAlerta = tiempoActual
                android.util.Log.w("MONITOREO", "¡ALERTA TEXTO! ($motivo) $porcentajeFloat% en $appOrigen")

                // Le avisamos al Servicio de Monitoreo que tome la foto
                onAlertaDetectada(motivo, porcentajeFloat, textoLimpio)
            }
        }
    }

    // --- FUNCIÓN 2: SE LLAMA DESPUÉS DE TOMAR LA FOTO Y GUARDA TODO ---
    fun registrarAlertaConImagen(motivo: String, porcentajeFloat: Float, textoLimpio: String, appOrigen: String, rutaImagen: String) {
        Thread {
            try {
                val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val porcentajeInt = porcentajeFloat.toInt()

                // Recortamos el texto feo para que la tarjeta de la bitácora se vea estética
                val fragmentoCorto = if (textoLimpio.length > 80) textoLimpio.substring(0, 80) + "..." else textoLimpio
                val detalleCompleto = "Alerta de Texto ($motivo): $porcentajeInt%\nApp: $appOrigen\nEvidencia: \"$fragmentoCorto\""

                // AHORA SÍ GUARDAMOS EN LA BASE DE DATOS CON LA RUTA DE LA FOTO INCLUIDA
                dbHelper.insertarRegistro(fecha = fecha, resultado = detalleCompleto, rutaImagen = rutaImagen)

                // Guardamos en Firebase (el correo automático)
                guardarAlertaTextoEnNube(context, porcentajeFloat, appOrigen, textoLimpio, rutaImagen)            } catch (e: Exception) {
                android.util.Log.e("Bitacora", "Error al guardar alerta texto: ${e.message}")
            }
        }.start()
    }

    fun cerrar() {
        interpreter?.close()
        interpreter = null
    }
}

// --- FUNCIÓN PARA ENVIAR EL CORREO DE ALERTA DE TEXTO ---
// --- FUNCIÓN PARA ENVIAR EL CORREO DE ALERTA DE TEXTO CON FOTO ---
fun guardarAlertaTextoEnNube(context: Context, porcentajeRiesgo: Float, appOrigen: String, textoInterceptado: String, rutaImagen: String) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val usuario = auth.currentUser
    val bd = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    if (usuario != null && usuario.email != null) {
        val correoPadre = usuario.email!!
        val fechaActual = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val porcentajeFormateado = String.format(java.util.Locale.getDefault(), "%.1f", porcentajeRiesgo)

        val fragmentoTexto = if (textoInterceptado.length > 200) textoInterceptado.substring(0, 200) + "..." else textoInterceptado

        val htmlBody = """
            <div style="font-family: Arial, sans-serif; color: #333; padding: 20px; border: 1px solid #ddd; border-radius: 10px; max-width: 600px;">
                <h2 style="color: #9C27B0; border-bottom: 2px solid #9C27B0; padding-bottom: 10px;">Parental AI Shield - Alerta de Texto (NLP)</h2>
                <p>Hola <b>${usuario.displayName ?: "Tutor"}</b>,</p>
                <p>Nuestro modelo de Lenguaje Natural ha detectado una conversación, búsqueda o texto con contenido potencialmente riesgoso.</p>
                
                <ul style="background-color: #f9f9f9; padding: 15px 15px 15px 35px; border-radius: 5px;">
                    <li><b>Fecha y Hora:</b> $fechaActual</li>
                    <li><b>Aplicación Origen:</b> $appOrigen</li>
                    <li><b>Nivel de Confianza:</b> <span style="color: #D32F2F; font-weight: bold;">$porcentajeFormateado%</span></li>
                </ul>

                <div style="background-color: #FCE4EC; padding: 12px; border-left: 4px solid #E91E63; margin-top: 15px; border-radius: 4px;">
                    <b>Fragmento de texto interceptado:</b><br>
                    <i>"$fragmentoTexto"</i>
                </div>
                <p style="font-size: 12px; color: #666; margin-top: 20px;">* Se ha adjuntado una captura de pantalla de la evidencia en este correo.</p>
            </div>
        """.trimIndent()

        // Preparamos el mapa del mensaje
        val messageMap = hashMapOf<String, Any>(
            "subject" to "💬 Alerta NLP: Texto de Riesgo Detectado",
            "html" to htmlBody
        )

        // --- MAGIA BASE64: CONVERTIMOS LA IMAGEN Y LA ADJUNTAMOS ---
        if (rutaImagen.isNotEmpty()) {
            val archivoImg = java.io.File(rutaImagen)
            if (archivoImg.exists()) {
                try {
                    val bytes = archivoImg.readBytes()
                    val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

                    messageMap["attachments"] = arrayListOf(
                        hashMapOf(
                            "filename" to "evidencia_texto_${System.currentTimeMillis()}.jpg",
                            "content" to base64Image,
                            "encoding" to "base64"
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("Correo", "Error convirtiendo imagen a Base64: ${e.message}")
                }
            }
        }

        val reporte = hashMapOf(
            "to" to arrayListOf(correoPadre),
            "message" to messageMap
        )

        bd.collection("AlertasNSFW").add(reporte)
    }
}