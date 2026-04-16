package com.example.aplicaciondetesis

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object GestorImagenes {

    fun guardarCapturaEnMemoria(context: Context, bitmap: Bitmap): String {
        val nombreArchivo = "captura_${UUID.randomUUID()}.jpg"
        val archivo = File(context.filesDir, nombreArchivo)

        FileOutputStream(archivo).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        return archivo.absolutePath
    }
}