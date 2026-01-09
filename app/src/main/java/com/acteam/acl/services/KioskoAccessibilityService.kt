package com.acteam.acl.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class KioskAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Filtramos eventos de tipo NOTIFICACIÓN o cambio de VENTANA (donde salen los Snackbars)
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val message = event.text.toString()

            if (message.contains("Error 500-sitef", ignoreCase = true)) {
                restartSitef()
            }
        }
    }

    private fun restartSitef() {
        val sitefPackage = "br.com.softwareexpress.sitef.example.v2" // Verifica el nombre real
        val mainAppPackage = "com.gaman.puntov_machine"

        // Lógica: Forzar cierre (si eres Device Owner) y abrir
        val intent = packageManager.getLaunchIntentForPackage(sitefPackage)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }

        // Esperar un momento y volver a la app principal
        Handler(Looper.getMainLooper()).postDelayed({
            val backToMain = packageManager.getLaunchIntentForPackage(mainAppPackage)
            backToMain?.let { startActivity(it) }
        }, 5000)
    }

    override fun onInterrupt() {}
}