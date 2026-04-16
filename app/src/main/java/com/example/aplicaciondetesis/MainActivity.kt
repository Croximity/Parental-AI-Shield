package com.example.aplicaciondetesis

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GestorPin {
    private const val PREFS_NAME = "SeguridadApp"
    private const val KEY_PIN = "pin_acceso"

    fun guardarPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun obtenerPin(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN, null)
    }

    fun borrarPin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PIN).apply()
    }
}

class MainActivity : ComponentActivity() {
    private var analizadorVisual: AnalizadorVisualNSFW? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            analizadorVisual = AnalizadorVisualNSFW(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Aviso: IA no cargada.", Toast.LENGTH_LONG).show()
        }

        val componentName = ComponentName(this, AdminReceiver::class.java)
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activa la protección para evitar que la aplicación sea desinstalada.")
            }
            startActivity(intent)
        }

        val dbHelper = BitacoraDbHelper(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    val context = LocalContext.current

                    var usuarioLogueado by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
                    var pinGuardado by remember { mutableStateOf(GestorPin.obtenerPin(context)) }
                    var appDesbloqueada by remember { mutableStateOf(false) }

                    // --- NUEVO: EL SALVOCONDUCTO ---
                    var navegandoInternamente by remember { mutableStateOf(false) }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) {
                                // Solo bloqueamos si NO estamos navegando a una pantalla interna
                                if (!navegandoInternamente) {
                                    appDesbloqueada = false
                                }
                            }
                            if (event == Lifecycle.Event.ON_RESUME) {
                                // Al regresar, desactivamos el salvoconducto para que el bloqueo vuelva a funcionar
                                navegandoInternamente = false
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (!usuarioLogueado) {
                        PantallaLogin(onLoginExitoso = {
                            usuarioLogueado = true
                            pinGuardado = GestorPin.obtenerPin(context)
                        })
                    } else if (pinGuardado == null) {
                        PantallaCrearPin(
                            onPinCreado = { nuevoPin ->
                                GestorPin.guardarPin(context, nuevoPin)
                                pinGuardado = nuevoPin
                                appDesbloqueada = true
                            },
                            onCancelar = {
                                FirebaseAuth.getInstance().signOut()
                                usuarioLogueado = false
                            }
                        )
                    } else {
                        // Pasamos el salvoconducto a la interfaz
                        PantallaDePruebaIA(
                            analizador = analizadorVisual,
                            dbHelper = dbHelper,
                            onLogout = {
                                usuarioLogueado = false
                                appDesbloqueada = false
                            },
                            onNavegarSeguro = { navegandoInternamente = true } // <--- Activador del salvoconducto
                        )

                        if (!appDesbloqueada) {
                            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                                PantallaIngresarPin(
                                    pinCorrecto = pinGuardado!!,
                                    onDesbloqueado = { appDesbloqueada = true },
                                    onOlvidePin = {
                                        FirebaseAuth.getInstance().signOut()
                                        GestorPin.borrarPin(context)
                                        usuarioLogueado = false
                                        pinGuardado = null
                                        appDesbloqueada = false
                                        Toast.makeText(context, "Inicia sesión nuevamente para crear un nuevo PIN", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- PANTALLAS DEL PIN Y LOGIN SE MANTIENEN IGUAL ---

@Composable
fun PantallaCrearPin(onPinCreado: (String) -> Unit, onCancelar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmarPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, contentDescription = "Seguridad", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Crea un PIN de Seguridad", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Requerido para abrir la aplicación.", textAlign = TextAlign.Center, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6) pin = it }, label = { Text("PIN (4 a 6 dígitos)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = confirmarPin, onValueChange = { if (it.length <= 6) confirmarPin = it }, label = { Text("Confirma tu PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())

        error?.let { Spacer(modifier = Modifier.height(8.dp)); Text(it, color = Color.Red) }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            if (pin.length < 4) error = "Mínimo 4 dígitos"
            else if (pin != confirmarPin) error = "No coinciden"
            else onPinCreado(pin)
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar PIN") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCancelar) { Text("Cancelar y Cerrar Sesión", color = Color.Gray) }
    }
}

@Composable
fun PantallaIngresarPin(pinCorrecto: String, onDesbloqueado: () -> Unit, onOlvidePin: () -> Unit) {
    var pinIngresado by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Aplicación Bloqueada", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(value = pinIngresado, onValueChange = { if (it.length <= 6) { pinIngresado = it; error = false } }, label = { Text("Ingresa tu PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), isError = error, modifier = Modifier.fillMaxWidth())
        if (error) Text("PIN incorrecto. Intenta de nuevo.", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { if (pinIngresado == pinCorrecto) onDesbloqueado() else { error = true; pinIngresado = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Desbloquear") }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onOlvidePin) { Text("¿Olvidaste tu PIN?", color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
fun PantallaLogin(onLoginExitoso: () -> Unit) {
    val context = LocalContext.current
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var confirmarContrasena by remember { mutableStateOf("") }
    var primerNombre by remember { mutableStateOf("") }
    var segundoNombre by remember { mutableStateOf("") }
    var primerApellido by remember { mutableStateOf("") }
    var modoRegistro by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    val auth = FirebaseAuth.getInstance()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Parental AI Shield", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(30.dp))

        if (modoRegistro) {
            OutlinedTextField(value = primerNombre, onValueChange = { primerNombre = it }, label = { Text("Primer Nombre") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = segundoNombre, onValueChange = { segundoNombre = it }, label = { Text("Segundo Nombre (Opcional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = primerApellido, onValueChange = { primerApellido = it }, label = { Text("Primer Apellido") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(value = correo, onValueChange = { correo = it }, label = { Text("Correo del Tutor") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = contrasena, onValueChange = { contrasena = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())

        if (modoRegistro) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = confirmarContrasena, onValueChange = { confirmarContrasena = it }, label = { Text("Confirmar Contraseña") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        }

        mensajeError?.let { Spacer(modifier = Modifier.height(8.dp)); Text(text = it, color = Color.Red, fontSize = 14.sp) }
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            if (correo.isNotEmpty() && contrasena.isNotEmpty() && (!modoRegistro || (confirmarContrasena.isNotEmpty() && primerNombre.isNotEmpty() && primerApellido.isNotEmpty()))) {
                mensajeError = null
                if (modoRegistro) {
                    if (contrasena == confirmarContrasena) {
                        auth.createUserWithEmailAndPassword(correo, contrasena).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName("$primerNombre $segundoNombre $primerApellido".trim()).build()
                                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                    user.sendEmailVerification().addOnCompleteListener {
                                        Toast.makeText(context, "Registro exitoso. Revisa tu correo.", Toast.LENGTH_LONG).show()
                                        onLoginExitoso()
                                    }
                                }
                            } else mensajeError = task.exception?.localizedMessage ?: "Error al registrar"
                        }
                    } else mensajeError = "Las contraseñas no coinciden"
                } else {
                    auth.signInWithEmailAndPassword(correo, contrasena).addOnCompleteListener { task ->
                        if (task.isSuccessful) onLoginExitoso() else mensajeError = "Credenciales incorrectas"
                    }
                }
            } else mensajeError = "Llena todos los campos obligatorios"
        }, modifier = Modifier.fillMaxWidth()) { Text(if (modoRegistro) "Registrar Cuenta" else "Iniciar Sesión") }

        TextButton(onClick = { modoRegistro = !modoRegistro; mensajeError = null }) { Text(if (modoRegistro) "¿Ya tienes cuenta? Inicia sesión" else "¿Nuevo usuario? Regístrate") }
        Spacer(modifier = Modifier.height(24.dp))
        Text("— O —", color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        BotonLoginGoogle(onLoginExitoso = { onLoginExitoso() })
    }
}

@Composable
fun MetricaItem(titulo: String, valor: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = valor, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = titulo, fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
    }
}

// --- PANEL PRINCIPAL CON SALVOCONDUCTO AÑADIDO ---
@Composable
fun PantallaDePruebaIA(
    analizador: AnalizadorVisualNSFW?,
    dbHelper: BitacoraDbHelper,
    onLogout: () -> Unit,
    onNavegarSeguro: () -> Unit // Recibimos la función salvoconducto
) {
    val context = LocalContext.current
    val usuarioActual = FirebaseAuth.getInstance().currentUser
    val lifecycleOwner = LocalLifecycleOwner.current

    var bitmapSeleccionado by remember { mutableStateOf<Bitmap?>(null) }
    var textoResultado by remember { mutableStateOf("Sube una imagen para analizar") }
    var colorResultado by remember { mutableStateOf(Color.Gray) }
    var menuExpandido by remember { mutableStateOf(false) }
    var mostrarInfoUsuario by remember { mutableStateOf(false) }

    var servicioActivo by remember { mutableStateOf(false) }
    var ignorandoBateria by remember { mutableStateOf(false) }

    var alertasHoy by remember { mutableIntStateOf(0) }
    var alertasVisuales by remember { mutableIntStateOf(0) }
    var alertasTexto by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val expectedComponentName = ComponentName(context, ServicioMonitoreoNSFW::class.java)
                val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                servicioActivo = enabledServices.contains(expectedComponentName.flattenToString(), ignoreCase = true)

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                ignorandoBateria = powerManager.isIgnoringBatteryOptimizations(context.packageName)

                val registrosActivos = dbHelper.obtenerTodosActivos()
                val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaHoyStr = formatoFecha.format(Date())

                alertasHoy = registrosActivos.count { it.fecha.startsWith(fechaHoyStr) }
                alertasVisuales = registrosActivos.count { it.resultadoAnalisis.contains("Positiva", ignoreCase = true) }
                alertasTexto = registrosActivos.count { it.resultadoAnalisis.contains("Texto", ignoreCase = true) || it.resultadoAnalisis.contains("Incógnito", ignoreCase = true) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val abrirGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val bitmapOriginal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                val bitmapProcesable = bitmapOriginal.copy(Bitmap.Config.ARGB_8888, true)
                bitmapSeleccionado = bitmapProcesable

                if (analizador != null) {
                    val probabilidadNSFW = analizador.evaluarImagen(bitmapProcesable)
                    val porcentaje = probabilidadNSFW * 100

                    if (probabilidadNSFW > 0.60f) {
                        textoResultado = String.format(Locale.getDefault(), "⚠️ ALERTA NSFW: %.1f%%", porcentaje)
                        colorResultado = Color.Red
                        analizador.procesarCapturaAutomatica(bitmapProcesable, "Prueba Manual")
                    } else {
                        textoResultado = String.format(Locale.getDefault(), "✅ SEGURO (SFW): %.1f%%", porcentaje)
                        colorResultado = Color(0xFF00C853)
                    }
                } else {
                    textoResultado = "La IA no se pudo inicializar."
                    colorResultado = Color.Red
                }
            } catch (e: Exception) {
                textoResultado = "Error al procesar: ${e.message}"
                colorResultado = Color.Red
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Parental AI Shield", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Box {
                IconButton(onClick = { menuExpandido = true }, modifier = Modifier.size(48.dp)) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú Principal", modifier = Modifier.size(28.dp))
                }
                DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                    DropdownMenuItem(text = { Text("Información de la cuenta") }, onClick = { menuExpandido = false; mostrarInfoUsuario = true })

                    // --- AQUÍ ACTIVAMOS EL SALVOCONDUCTO AL IR A LA BITÁCORA ---
                    DropdownMenuItem(text = { Text("Ver Bitácora de Alertas") }, onClick = {
                        menuExpandido = false
                        onNavegarSeguro() // ¡Bandera activa!
                        context.startActivity(Intent(context, BitacoraActivity::class.java))
                    })

                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Cerrar Sesión", color = Color.Red, fontWeight = FontWeight.Bold) }, onClick = { menuExpandido = false; FirebaseAuth.getInstance().signOut(); onLogout() })
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Resumen de Actividad", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricaItem("Total Hoy", alertasHoy.toString(), Color(0xFF1976D2))
                    MetricaItem("Imágenes", alertasVisuales.toString(), Color(0xFFD32F2F))
                    MetricaItem("Textos", alertasTexto.toString(), Color(0xFF9C27B0))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (servicioActivo) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (servicioActivo) "✔ Monitoreo de apps ACTIVO" else "✘ Monitoreo DESACTIVADO", fontWeight = FontWeight.Bold, color = if (servicioActivo) Color(0xFF2E7D32) else Color(0xFFC62828), textAlign = TextAlign.Center)
                if (!servicioActivo) { Spacer(modifier = Modifier.height(8.dp)); Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Conceder Permiso") } }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (ignorandoBateria) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (ignorandoBateria) "✔ Escudo de Batería ACTIVO" else "⚠️ Batería Restringida (Peligro)", fontWeight = FontWeight.Bold, color = if (ignorandoBateria) Color(0xFF2E7D32) else Color(0xFFE65100), textAlign = TextAlign.Center)
                if (!ignorandoBateria) { Spacer(modifier = Modifier.height(8.dp)); Button(onClick = { val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }; context.startActivity(intent) }) { Text("Blindar Aplicación") } }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Área de Pruebas del Modelo", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        // --- TAMBIÉN ACTIVAMOS EL SALVOCONDUCTO PARA LA GALERÍA ---
        Button(
            onClick = {
                onNavegarSeguro()
                abrirGaleria.launch("image/*")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Probar imagen de galería manual")
        }

        Spacer(modifier = Modifier.height(16.dp))

        bitmapSeleccionado?.let { Card(elevation = CardDefaults.cardElevation(8.dp)) { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(150.dp).padding(8.dp)) } }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = textoResultado, color = colorResultado, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (mostrarInfoUsuario) {
        AlertDialog(
            onDismissRequest = { mostrarInfoUsuario = false },
            title = { Text("Datos del Tutor Responsable", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Nombre: ${usuarioActual?.displayName ?: "No registrado"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Correo: ${usuarioActual?.email ?: "Desconocido"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Estado: Cuenta Activa", color = Color(0xFF2E7D32))
                }
            },
            confirmButton = { TextButton(onClick = { mostrarInfoUsuario = false }) { Text("Entendido") } }
        )
    }
}

// ... BotonLoginGoogle se mantiene igual ...
@Composable
fun BotonLoginGoogle(onLoginExitoso: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    Button(
        onClick = {
            coroutineScope.launch {
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId("660724653555-qirtcc6tcdh4p3j70v8i2pnnuq07rdhh.apps.googleusercontent.com")
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                    val result = CredentialManager.create(context).getCredential(request = request, context = context)

                    val credential = result.credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                        auth.signInWithCredential(authCredential).addOnCompleteListener { task ->
                            if (task.isSuccessful) onLoginExitoso()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LOGIN", "Cancelado o error: ${e.message}")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
    ) {
        Text("Vincular correo del Tutor (Google)", color = Color.White)
    }
}