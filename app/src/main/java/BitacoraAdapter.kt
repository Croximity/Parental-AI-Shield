package com.example.aplicaciondetesis

import com.example.aplicaciondetesis.R
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aplicaciondetesis.com.example.aplicaciondetesis.RegistroBitacora
import java.io.File

class BitacoraAdapter(private val listaRegistros: List<RegistroBitacora>) :
    RecyclerView.Adapter<BitacoraAdapter.BitacoraViewHolder>() {

    class BitacoraViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCaptura: ImageView = view.findViewById(R.id.ivCaptura)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvResultado: TextView = view.findViewById(R.id.tvResultado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BitacoraViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bitacora, parent, false)
        return BitacoraViewHolder(view)
    }

    override fun onBindViewHolder(holder: BitacoraViewHolder, position: Int) {
        val registro = listaRegistros[position]

        holder.tvFecha.text = registro.fecha
        holder.tvResultado.text = registro.resultadoAnalisis

        // Cargamos la imagen desde la ruta guardada
        val archivoImagen = File(registro.rutaImagen)
        if (archivoImagen.exists()) {
            holder.ivCaptura.setImageURI(Uri.fromFile(archivoImagen))
        }
    }

    override fun getItemCount() = listaRegistros.size
}