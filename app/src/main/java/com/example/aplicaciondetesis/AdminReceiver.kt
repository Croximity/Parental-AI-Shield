package com.example.aplicaciondetesis

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Seguridad activada. App protegida.", Toast.LENGTH_SHORT).show()
    }

    // Este es el mensaje disuasivo que le saldrá a quien intente quitar el permiso
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "ADVERTENCIA: Si desactivas este administrador, la supervisión de Parental AI Shield se detendrá y el dispositivo quedará vulnerable."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Seguridad desactivada.", Toast.LENGTH_SHORT).show()
    }
}