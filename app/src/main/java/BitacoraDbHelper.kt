package com.example.aplicaciondetesis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Actualizamos el modelo para que sepa si está en la papelera
data class RegistroBitacora(
    val id: Int,
    val fecha: String,
    val resultadoAnalisis: String,
    val rutaImagen: String,
    val enPapelera: Int = 0 // 0 = Activo, 1 = En Papelera
)

class BitacoraDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_VERSION = 2 // Subimos la versión para aplicar los cambios
        private const val DATABASE_NAME = "BitacoraNSFW.db"
        const val TABLE_NAME = "registros"
        const val COLUMN_ID = "id"
        const val COLUMN_FECHA = "fecha"
        const val COLUMN_RESULTADO = "resultado"
        const val COLUMN_RUTA = "ruta"
        const val COLUMN_PAPELERA = "enPapelera"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_FECHA + " TEXT,"
                + COLUMN_RESULTADO + " TEXT,"
                + COLUMN_RUTA + " TEXT,"
                + COLUMN_PAPELERA + " INTEGER DEFAULT 0" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertarRegistro(fecha: String, resultado: String, rutaImagen: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FECHA, fecha)
            put(COLUMN_RESULTADO, resultado)
            put(COLUMN_RUTA, rutaImagen)
            put(COLUMN_PAPELERA, 0)
        }
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun obtenerTodosActivos(): List<RegistroBitacora> {
        return leerRegistrosFiltrados(0)
    }

    fun obtenerPapelera(): List<RegistroBitacora> {
        return leerRegistrosFiltrados(1)
    }

    // Por compatibilidad si lo usas en otro lado
    fun obtenerTodos(): List<RegistroBitacora> = obtenerTodosActivos()

    private fun leerRegistrosFiltrados(estadoPapelera: Int): List<RegistroBitacora> {
        val lista = mutableListOf<RegistroBitacora>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_PAPELERA = $estadoPapelera ORDER BY $COLUMN_ID DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val fecha = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA))
                val res = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RESULTADO))
                val ruta = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RUTA))
                val papelera = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PAPELERA))

                lista.add(RegistroBitacora(id, fecha, res, ruta, papelera))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }

    fun cambiarEstadoPapelera(id: Int, enPapelera: Int) {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COLUMN_PAPELERA, enPapelera) }
        db.update(TABLE_NAME, values, "$COLUMN_ID=?", arrayOf(id.toString()))
        db.close()
    }

    fun eliminarDefinitivo(id: Int, rutaImagen: String) {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_ID=?", arrayOf(id.toString()))
        db.close()
        val file = File(rutaImagen)
        if (file.exists()) {
            file.delete()
        }
    }

    // --- LA PURGA AUTOMÁTICA DE 14 DÍAS ---
    fun limpiarRegistrosAntiguos() {
        val db = this.writableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fechaActual = Date()

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val fechaStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA))
                val ruta = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RUTA))

                try {
                    val fechaRegistro = format.parse(fechaStr)
                    if (fechaRegistro != null) {
                        val diff = fechaActual.time - fechaRegistro.time
                        val dias = diff / (1000 * 60 * 60 * 24)

                        // Si pasó el límite de 14 días, lo borramos permanentemente
                        if (dias >= 14) {
                            db.delete(TABLE_NAME, "$COLUMN_ID=?", arrayOf(id.toString()))
                            val file = File(ruta)
                            if (file.exists()) file.delete()
                        }
                    }
                } catch (e: Exception) { }
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
    }
    // --- NUEVO: VACIAR PAPELERA COMPLETAMENTE ---
    fun vaciarPapelera() {
        val db = this.writableDatabase

        // 1. Primero buscamos las rutas de las imágenes para borrarlas de la memoria física
        val cursor = db.rawQuery("SELECT $COLUMN_RUTA FROM $TABLE_NAME WHERE $COLUMN_PAPELERA = 1", null)
        if (cursor.moveToFirst()) {
            do {
                val ruta = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RUTA))
                if (ruta.isNotEmpty()) {
                    val file = File(ruta)
                    if (file.exists()) file.delete()
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        // 2. Luego borramos todos los registros de la base de datos
        db.delete(TABLE_NAME, "$COLUMN_PAPELERA=?", arrayOf("1"))
        db.close()
    }
}