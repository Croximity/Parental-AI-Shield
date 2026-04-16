package com.example.aplicaciondetesis

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class BitacoraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dbHelper = BitacoraDbHelper(this)

        setContent {
            MaterialTheme {
                PantallaBitacora(dbHelper = dbHelper, onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBitacora(dbHelper: BitacoraDbHelper, onBackClick: () -> Unit) {
    val context = LocalContext.current

    var mostrandoPapelera by remember { mutableStateOf(false) }
    var registros by remember { mutableStateOf(listOf<RegistroBitacora>()) }

    var indicePestana by remember { mutableStateOf(0) }
    val titulosPestanas = listOf("Todas", "Visuales", "Texto")
    var mostrarDialogoCorreo by remember { mutableStateOf(false) }
    var mostrarDialogoVaciar by remember { mutableStateOf(false) }
    var registroABorrarDefinitivo by remember { mutableStateOf<RegistroBitacora?>(null) }

    // --- NUEVO: Estado para saber qué foto mostrar a pantalla completa ---
    var rutaImagenExpandida by remember { mutableStateOf<String?>(null) }

    fun recargarDatos() {
        dbHelper.limpiarRegistrosAntiguos()
        registros = if (mostrandoPapelera) dbHelper.obtenerPapelera() else dbHelper.obtenerTodosActivos()
    }

    LaunchedEffect(mostrandoPapelera) {
        recargarDatos()
        indicePestana = 0
    }

    val registrosMostrados = registros.filter { reg ->
        when (indicePestana) {
            1 -> reg.resultadoAnalisis.contains("Positiva", ignoreCase = true)
            2 -> reg.resultadoAnalisis.contains("Texto", ignoreCase = true) || reg.resultadoAnalisis.contains("Incógnito", ignoreCase = true)
            else -> true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mostrandoPapelera) "Papelera" else "Bitácora", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White) }
                },
                actions = {
                    if (mostrandoPapelera && registros.isNotEmpty()) {
                        TextButton(onClick = { mostrarDialogoVaciar = true }) {
                            Text("VACIAR", color = Color(0xFFFFCDD2), fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { mostrandoPapelera = !mostrandoPapelera }) {
                        Icon(if (mostrandoPapelera) Icons.Filled.Refresh else Icons.Filled.Delete, contentDescription = "Vista", tint = Color.White)
                    }
                    if (!mostrandoPapelera && registrosMostrados.isNotEmpty()) {
                        IconButton(onClick = { mostrarDialogoCorreo = true }) {
                            Icon(Icons.Filled.Email, contentDescription = "Enviar", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (mostrandoPapelera) Color(0xFF616161) else MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = Color(0xFFFFF3E0), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "⚠️ Los registros se conservan por 14 días. Luego se eliminan permanentemente.",
                        modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
                    )
                }

                TabRow(selectedTabIndex = indicePestana, containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary) {
                    titulosPestanas.forEachIndexed { index, titulo ->
                        Tab(
                            selected = indicePestana == index,
                            onClick = { indicePestana = index },
                            text = { Text(titulo, fontWeight = if (indicePestana == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                if (registrosMostrados.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text(if (mostrandoPapelera) "La papelera está vacía" else "No hay alertas en esta categoría", fontSize = 16.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(registrosMostrados) { registro ->
                            ItemRegistro(
                                registro = registro,
                                esPapelera = mostrandoPapelera,
                                onMoveToBin = {
                                    dbHelper.cambiarEstadoPapelera(registro.id, 1)
                                    recargarDatos()
                                    Toast.makeText(context, "Enviado a papelera", Toast.LENGTH_SHORT).show()
                                },
                                onRestore = {
                                    dbHelper.cambiarEstadoPapelera(registro.id, 0)
                                    recargarDatos()
                                    Toast.makeText(context, "Registro restaurado", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteForever = { registroABorrarDefinitivo = registro },
                                // --- NUEVO: Pasamos la instrucción de qué foto abrir ---
                                onExpandirImagen = { ruta -> rutaImagenExpandida = ruta }
                            )
                        }
                    }
                }
            }

            // --- NUEVO: VISOR DE IMAGEN FLOTANTE ---
            // Se dibuja "encima" de todo lo demás si rutaImagenExpandida no es null
            rutaImagenExpandida?.let { ruta ->
                VisorImagenFlotante(rutaImagen = ruta, onCerrar = { rutaImagenExpandida = null })
            }
        }

        // ... (Tus diálogos de alerta se mantienen igual abajo)
        if (mostrarDialogoCorreo) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoCorreo = false },
                title = { Text("Exportar Categoría", fontWeight = FontWeight.Bold) },
                text = { Text("¿Deseas enviar por correo el historial de alertas que estás viendo en pantalla (Máximo 10)?") },
                confirmButton = {
                    Button(onClick = {
                        mostrarDialogoCorreo = false
                        enviarHistorialPorCorreo(context, registrosMostrados)
                    }) { Text("Sí, Enviar") }
                },
                dismissButton = { TextButton(onClick = { mostrarDialogoCorreo = false }) { Text("Cancelar") } }
            )
        }

        if (mostrarDialogoVaciar) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoVaciar = false },
                title = { Text("Vaciar Papelera", fontWeight = FontWeight.Bold, color = Color.Red) },
                text = { Text("¿Estás seguro de que deseas destruir TODOS los registros e imágenes de la papelera? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(colors = ButtonDefaults.buttonColors(containerColor = Color.Red), onClick = {
                        mostrarDialogoVaciar = false
                        dbHelper.vaciarPapelera()
                        recargarDatos()
                        Toast.makeText(context, "Papelera vaciada por completo", Toast.LENGTH_SHORT).show()
                    }) { Text("Sí, Vaciar Todo") }
                },
                dismissButton = { TextButton(onClick = { mostrarDialogoVaciar = false }) { Text("Cancelar") } }
            )
        }

        if (registroABorrarDefinitivo != null) {
            AlertDialog(
                onDismissRequest = { registroABorrarDefinitivo = null },
                title = { Text("Eliminar Definitivamente", fontWeight = FontWeight.Bold, color = Color.Red) },
                text = { Text("Se destruirá este registro y su imagen para siempre. ¿Continuar?") },
                confirmButton = {
                    Button(colors = ButtonDefaults.buttonColors(containerColor = Color.Red), onClick = {
                        dbHelper.eliminarDefinitivo(registroABorrarDefinitivo!!.id, registroABorrarDefinitivo!!.rutaImagen)
                        registroABorrarDefinitivo = null
                        recargarDatos()
                        Toast.makeText(context, "Destruido", Toast.LENGTH_SHORT).show()
                    }) { Text("Eliminar") }
                },
                dismissButton = { TextButton(onClick = { registroABorrarDefinitivo = null }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
fun ItemRegistro(
    registro: RegistroBitacora,
    esPapelera: Boolean,
    onMoveToBin: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    // --- NUEVO PARÁMETRO DE CLIC ---
    onExpandirImagen: (String) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expandido = !expandido }
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = if (expandido) Alignment.Top else Alignment.CenterVertically) {

            if (registro.rutaImagen.isNotEmpty()) {
                val file = File(registro.rutaImagen)
                if (file.exists()) {
                    val uri = Uri.fromFile(file)
                    Image(
                        painter = rememberAsyncImagePainter(model = uri),
                        contentDescription = "Captura",
                        modifier = Modifier
                            .size(if (expandido) 120.dp else 80.dp)
                            // --- NUEVO: La imagen reacciona al clic SOLO si la tarjeta ya está expandida ---
                            .clickable(enabled = expandido) {
                                onExpandirImagen(registro.rutaImagen)
                            },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.size(80.dp).background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Warning, contentDescription = "Sin imagen", tint = Color.Gray)
                    }
                }
            } else {
                Box(modifier = Modifier.size(80.dp).background(Color(0xFFF3E5F5)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Warning, contentDescription = "Alerta de Texto", tint = Color(0xFF9C27B0), modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = registro.fecha, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))

                val colorAlerta = if (registro.resultadoAnalisis.contains("Texto") || registro.resultadoAnalisis.contains("Incógnito")) Color(0xFF9C27B0) else Color.Red

                Text(
                    text = registro.resultadoAnalisis,
                    color = colorAlerta,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expandido) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )

                if (!expandido && registro.resultadoAnalisis.length > 100) {
                    Text(text = "Toca para ver más...", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                // --- NUEVO: Pista visual para avisar que la foto se puede abrir ---
                if (expandido && registro.rutaImagen.isNotEmpty()) {
                    Text(
                        text = "Toca la imagen para ver a detalle",
                        color = Color(0xFF1976D2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (esPapelera) {
                    IconButton(onClick = onRestore) { Icon(Icons.Filled.Refresh, contentDescription = "Restaurar", tint = Color(0xFF2E7D32)) }
                    IconButton(onClick = onDeleteForever) { Icon(Icons.Filled.Delete, contentDescription = "Destruir", tint = Color.Red) }
                } else {
                    IconButton(onClick = onMoveToBin) { Icon(Icons.Filled.Delete, contentDescription = "Papelera", tint = Color.Gray) }
                }
            }
        }
    }
}

// --- NUEVO: EL DIÁLOGO FLOTANTE PARA VER LA FOTO EN GRANDE ---
@Composable
fun VisorImagenFlotante(rutaImagen: String, onCerrar: () -> Unit) {
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Permite usar toda la pantalla
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)) // Fondo oscuro
                .clickable { onCerrar() }, // Se cierra tocando afuera de la foto
            contentAlignment = Alignment.Center
        ) {
            val uri = Uri.fromFile(File(rutaImagen))
            Card(
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.padding(16.dp).wrapContentSize()
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = uri),
                    contentDescription = "Evidencia Expandida",
                    modifier = Modifier.wrapContentSize(),
                    contentScale = ContentScale.Fit // Evita que se recorte la imagen original
                )
            }

            Text(
                text = "X",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .clickable { onCerrar() }
            )
        }
    }
}

// --- ENVÍO MASIVO CON IMÁGENES INCRUSTADAS Y EVIDENCIA DE TEXTO ---
fun enviarHistorialPorCorreo(context: Context, registros: List<RegistroBitacora>) {
    val auth = FirebaseAuth.getInstance()
    val usuario = auth.currentUser
    val bd = FirebaseFirestore.getInstance()

    if (usuario != null && usuario.email != null) {
        val correoPadre = usuario.email!!

        var listaHtml = ""
        val attachments = arrayListOf<HashMap<String, String>>()
        val registrosAEnviar = registros.take(10)

        for ((index, reg) in registrosAEnviar.withIndex()) {
            var imagenHtml = ""

            if (reg.rutaImagen.isNotEmpty()) {
                val file = File(reg.rutaImagen)
                if (file.exists()) {
                    try {
                        val bitmapOriginal = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        val proporcion = 300.0f / bitmapOriginal.width
                        val nuevoAlto = (bitmapOriginal.height * proporcion).toInt()
                        val bitmapMiniatura = android.graphics.Bitmap.createScaledBitmap(bitmapOriginal, 300, nuevoAlto, true)

                        val baos = java.io.ByteArrayOutputStream()
                        bitmapMiniatura.compress(android.graphics.Bitmap.CompressFormat.JPEG, 40, baos)
                        val imageBytes = baos.toByteArray()
                        val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

                        val cid = "img_bitacora_$index"

                        attachments.add(hashMapOf(
                            "filename" to "evidencia_${index+1}.jpg",
                            "content" to base64Image,
                            "encoding" to "base64",
                            "cid" to cid
                        ))

                        imagenHtml = "<br><img src='cid:$cid' style='max-width: 250px; border-radius: 8px; border: 1px solid #ccc; margin-top: 10px;' alt='Evidencia'>"

                        bitmapOriginal.recycle()
                        if (bitmapMiniatura != bitmapOriginal) bitmapMiniatura.recycle()

                    } catch (e: Exception) {
                        imagenHtml = "<br><p style='color:red; font-size:12px;'><i>[Error al procesar imagen]</i></p>"
                    }
                }
            }

            val colorTexto = if (reg.resultadoAnalisis.contains("Texto") || reg.resultadoAnalisis.contains("Incógnito")) "#9C27B0" else "#D32F2F"

            val detalleFormateado = reg.resultadoAnalisis.replace("\n", "<br>")

            listaHtml += """
                <li style="margin-bottom: 20px; border-bottom: 1px solid #ccc; padding-bottom: 15px;">
                    <b>Fecha y Hora:</b> ${reg.fecha} <br>
                    <span style="color: $colorTexto;"><b>$detalleFormateado</b></span>
                    $imagenHtml
                </li>
            """.trimIndent()
        }

        val htmlBody = """
            <div style="font-family: Arial, sans-serif; color: #333; padding: 20px; border: 1px solid #ddd; border-radius: 10px; max-width: 600px;">
                <h2 style="color: #1976D2; border-bottom: 2px solid #1976D2; padding-bottom: 10px;">Parental AI Shield - Reporte Categorizado</h2>
                <p>Hola <b>${usuario.displayName ?: "Tutor"}</b>,</p>
                <p>A continuación encontrarás el detalle de las últimas alertas registradas en la categoría seleccionada (Máx. 10):</p>
                
                <ul style="background-color: #f9f9f9; padding: 15px 15px 15px 35px; border-radius: 5px;">
                    $listaHtml
                </ul>
                
                <p style="font-size: 11px; color: #777; margin-top: 20px;">*Nota: Las evidencias visuales se envían cifradas para proteger la privacidad del menor.</p>
            </div>
        """.trimIndent()

        val messageData = hashMapOf<String, Any>(
            "subject" to "📄 Reporte de Alertas: Parental AI Shield",
            "html" to htmlBody
        )
        if (attachments.isNotEmpty()) {
            messageData["attachments"] = attachments
        }

        val reporte = hashMapOf("to" to arrayListOf(correoPadre), "message" to messageData)

        bd.collection("AlertasNSFW").add(reporte)
            .addOnSuccessListener { Toast.makeText(context, "Reporte enviado a tu bandeja", Toast.LENGTH_LONG).show() }
            .addOnFailureListener { Toast.makeText(context, "Error al enviar", Toast.LENGTH_LONG).show() }
    } else {
        Toast.makeText(context, "Inicia sesión para enviar reportes", Toast.LENGTH_LONG).show()
    }
}