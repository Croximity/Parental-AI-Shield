package com.example.aplicaciondetesis

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class AnalizadorVisualNSFW(private val context: Context) {

    private var interpreter: Interpreter? = null
    private lateinit var byteBuffer: ByteBuffer
    private val dbHelper = BitacoraDbHelper(context)

    private var tiempoUltimaAlerta: Long = 0L
    private val TIEMPO_DESCANSO_MS: Long = 60000L

    init {
        val modelBuffer = loadModelFile(context, "modelo_vision_nsfw_PRO.tflite")
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(modelBuffer, options)
        byteBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun evaluarImagen(bitmap: Bitmap): Float {
        val dimension = Math.min(bitmap.width, bitmap.height)
        val x = (bitmap.width - dimension) / 2
        val y = (bitmap.height - dimension) / 2
        val imagenCuadrada = Bitmap.createBitmap(bitmap, x, y, dimension, dimension)

        val resizedBitmap = Bitmap.createScaledBitmap(imagenCuadrada, 224, 224, true)
        val intValues = IntArray(224 * 224)
        resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        byteBuffer.rewind()
        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF).toFloat()
            val g = (pixelValue shr 8 and 0xFF).toFloat()
            val b = (pixelValue and 0xFF).toFloat()

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        val outputBuffer = Array(1) { FloatArray(1) }
        interpreter?.run(byteBuffer, outputBuffer)

        if (imagenCuadrada != bitmap) imagenCuadrada.recycle()
        if (resizedBitmap != imagenCuadrada) resizedBitmap.recycle()

        return outputBuffer[0][0]
    }

    fun procesarCapturaAutomatica(bitmapCaptura: Bitmap, appOrigen: String) {
        val probabilidadNSFW = evaluarImagen(bitmapCaptura)
        val porcentajeFloat = probabilidadNSFW * 100

        if (probabilidadNSFW > 0.60f) {
            val tiempoActual = System.currentTimeMillis()

            if (tiempoActual - tiempoUltimaAlerta >= TIEMPO_DESCANSO_MS) {
                tiempoUltimaAlerta = tiempoActual
                android.util.Log.w("MONITOREO", "¡ALERTA AUTOMÁTICA ENVIADA! $porcentajeFloat% en $appOrigen")

                Thread {
                    try {
                        val ruta = GestorImagenes.guardarCapturaEnMemoria(context, bitmapCaptura)
                        val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        val porcentajeInt = porcentajeFloat.toInt()

                        dbHelper.insertarRegistro(
                            fecha = fecha,
                            resultado = "Detección Positiva: $porcentajeInt%",
                            rutaImagen = ruta
                        )

                        // ¡AQUÍ PASAMOS LA IMAGEN AL CORREO!
                        guardarAlertaEnNube(context, porcentajeFloat, appOrigen, false, bitmapCaptura)
                        android.util.Log.d("Bitacora", "¡NSFW Guardado en Local y Nube con Imagen!")
                    } catch (e: Exception) {
                        android.util.Log.e("Bitacora", "Error al guardar: ${e.message}")
                    } finally {
                        bitmapCaptura.recycle()
                    }
                }.start()
            } else {
                bitmapCaptura.recycle()
            }
        } else {
            bitmapCaptura.recycle()
        }
    }
    fun procesarAlertaIncognito(appOrigen: String) {
        Thread {
            try {
                val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

                // Añadimos el salto de línea y la app de origen
                val detalleCompleto = "Actividad Sospechosa: Modo Incógnito (> 2 min)\nApp: $appOrigen"

                dbHelper.insertarRegistro(fecha = fecha, resultado = detalleCompleto, rutaImagen = "")
                guardarAlertaIncognitoEnNube(context, appOrigen, fecha)
            } catch (e: Exception) {
                android.util.Log.e("Bitacora", "Error al guardar alerta incógnito: ${e.message}")
            }
        }.start()
    }

    fun cerrar() {
        interpreter?.close()
        interpreter = null
    }
}

// --- FUNCIÓN DE NUBE ACTUALIZADA CON BASE64 ---
fun guardarAlertaEnNube(context: Context, porcentajeNSFW: Float, appOrigen: String, esPruebaManual: Boolean = false, bitmapAdjunto: Bitmap? = null) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val usuario = auth.currentUser
    val bd = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    if (usuario != null && usuario.email != null) {
        val correoPadre = usuario.email!!
        val fechaActual = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val porcentajeFormateado = String.format(java.util.Locale.getDefault(), "%.1f", porcentajeNSFW)

        val messageData = hashMapOf<String, Any>(
            "subject" to "⚠️ Alerta de Seguridad: Contenido Inapropiado Detectado"
        )

        var htmlAdjunto = ""

        // --- CONVERSIÓN DE IMAGEN A TEXTO (PRIVACIDAD TOTAL) ---
        if (bitmapAdjunto != null) {
            try {
                val baos = java.io.ByteArrayOutputStream()
                // Comprimimos al 60% para no saturar el peso del correo
                bitmapAdjunto.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val imageBytes = baos.toByteArray()
                val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

                val attachments = arrayListOf(
                    hashMapOf(
                        "filename" to "evidencia_alerta.jpg",
                        "content" to base64Image,
                        "encoding" to "base64",
                        "cid" to "alerta_img" // ID para mostrarla en el HTML
                    )
                )
                messageData["attachments"] = attachments
                htmlAdjunto = """
                    <div style="margin-top: 15px;">
                        <b>Evidencia Visual:</b><br>
                        <img src="cid:alerta_img" style="max-width: 300px; border-radius: 8px; border: 1px solid #ccc; margin-top: 10px;" alt="Evidencia Segura">
                    </div>
                """.trimIndent()
            } catch (e: Exception) {
                android.util.Log.e("CORREO", "Error adjuntando imagen: ${e.message}")
            }
        }

        messageData["html"] = """
            <div style="font-family: Arial, sans-serif; color: #333; padding: 20px; border: 1px solid #ddd; border-radius: 10px;">
                <h2 style="color: #D32F2F;">Parental AI Shield</h2>
                <p>Hola <b>${usuario.displayName ?: "Tutor"}</b>,</p>
                <p>Nuestro sistema de Inteligencia Artificial ha detectado actividad sospechosa en el dispositivo monitoreado.</p>
                <ul style="background-color: #f9f9f9; padding: 15px; border-radius: 5px;">
                    <li><b>Fecha y Hora:</b> $fechaActual</li>
                    <li><b>Aplicación Origen:</b> $appOrigen</li>
                    <li><b>Probabilidad NSFW:</b> $porcentajeFormateado%</li>
                </ul>
                $htmlAdjunto
                <p style="font-size: 11px; color: #777; margin-top: 15px;">*Imagen enviada mediante codificación segura (Base64) para proteger la privacidad.</p>
            </div>
        """.trimIndent()

        val reporte = hashMapOf(
            "to" to arrayListOf(correoPadre),
            "message" to messageData,
            "correoTutor" to correoPadre,
            "fechaHora" to fechaActual,
            "nivelConfianza" to "$porcentajeFormateado%",
            "aplicacionOrigen" to appOrigen,
            "nombreTutor" to (usuario.displayName ?: "Tutor")
        )

        bd.collection("AlertasNSFW").add(reporte)
            .addOnSuccessListener {
                if (esPruebaManual) android.widget.Toast.makeText(context, "Alerta enviada con imagen", android.widget.Toast.LENGTH_SHORT).show()
            }
    }
}
fun guardarAlertaIncognitoEnNube(context: Context, appOrigen: String, fechaActual: String) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val usuario = auth.currentUser
    val bd = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    if (usuario != null && usuario.email != null) {
        val correoPadre = usuario.email!!

        val htmlBody = """
            <div style="font-family: Arial, sans-serif; color: #333; padding: 20px; border: 1px solid #ddd; border-radius: 10px;">
                <h2 style="color: #FF9800;">Parental AI Shield - Alerta de Comportamiento</h2>
                <p>Hola <b>${usuario.displayName ?: "Tutor"}</b>,</p>
                <p>Nuestro sistema de monitoreo de accesibilidad ha detectado una actividad inusual en el dispositivo.</p>
                <ul style="background-color: #f9f9f9; padding: 15px; border-radius: 5px;">
                    <li><b>Fecha y Hora:</b> $fechaActual</li>
                    <li><b>Aplicación Origen:</b> $appOrigen</li>
                    <li><b>Evento Detectado:</b> Uso prolongado de Navegación Privada / Modo Incógnito (Más de 2 minutos).</li>
                </ul>
                <p style="font-size: 11px; color: #777; margin-top: 15px;">*Nota técnica: Por diseño de seguridad del sistema operativo (FLAG_SECURE), las capturas de pantalla están bloqueadas durante la navegación privada, por lo que no se adjunta evidencia visual.</p>
            </div>
        """.trimIndent()

        val reporte = hashMapOf(
            "to" to arrayListOf(correoPadre),
            "message" to hashMapOf(
                "subject" to "🕵️ Actividad Sospechosa: Uso de Modo Incógnito Detectado",
                "html" to htmlBody
            ),
            "correoTutor" to correoPadre,
            "fechaHora" to fechaActual,
            "nivelConfianza" to "Navegación Oculta",
            "aplicacionOrigen" to appOrigen,
            "nombreTutor" to (usuario.displayName ?: "Tutor")
        )

        bd.collection("AlertasNSFW").add(reporte)
    }
}