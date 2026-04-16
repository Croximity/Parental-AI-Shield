package com.example.aplicaciondetesis.com.example.aplicaciondetesis

data class RegistroBitacora(
    val id: Int = 0, // Por defecto 0 para cuando creamos uno nuevo
    val fecha: String,
    val resultadoAnalisis: String,
    val rutaImagen: String
)