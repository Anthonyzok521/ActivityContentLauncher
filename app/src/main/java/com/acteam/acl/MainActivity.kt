package com.acteam.acl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.acteam.acl.models.*
import com.acteam.acl.ui.ACLScreen
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private var isKioskRunning by mutableStateOf(true)
    private var isSequencePaused by mutableStateOf(false) // Control para no iniciar apps mientras configuramos
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminName: ComponentName

    private val alreadyRunOnceApps = mutableSetOf<String>()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readJsonFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "acl-db")
            .fallbackToDestructiveMigration()
            .build()

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminName = ComponentName(this, DeviceAdminReceiver::class.java)

        hideSystemUI()

        setContent {
            val currentQueue: List<AppEntity> by db.appDao().getAllApps().collectAsState(initial = emptyList())

            ACLScreen(
                appQueue = currentQueue,
                availableApps = getInstalledApps(),
                onAddApp = { pkg, label, seconds, onlyOnce ->
                    lifecycleScope.launch {
                        db.appDao().insertApp(
                            AppEntity(
                                packageName = pkg,
                                label = label,
                                delaySeconds = seconds,
                                orderIndex = currentQueue.size,
                                runOnlyOnce = onlyOnce
                            )
                        )
                    }
                },
                onRemoveApp = { id -> lifecycleScope.launch { db.appDao().deleteApp(id) } },
                onExitKiosk = { exitToSettings() },
                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                onExport = { exportConfig() },
                onImportRequest = { importLauncher.launch("application/json") },
                isPaused = isSequencePaused,
                onTogglePause = { isSequencePaused = it }
            )
        }

        startLaunchSequence()
    }

    private fun startLaunchSequence() {
        lifecycleScope.launch {
            delay(3000)
            while (isKioskRunning) {
                if (isSequencePaused) {
                    delay(1000)
                    continue
                }

                val queue = db.appDao().getQueueSnapshot()
                if (queue.isEmpty()) {
                    delay(5000)
                    continue
                }

                for (app in queue) {
                    if (!isKioskRunning || isSequencePaused) break

                    // Lógica de "Ejecutar una sola vez"
                    if (app.runOnlyOnce && alreadyRunOnceApps.contains(app.packageName)) {
                        continue
                    }

                    launchApp(app.packageName)
                    alreadyRunOnceApps.add(app.packageName)
                    delay(app.delaySeconds * 1000L)
                }
            }
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            try {
                if (dpm.isDeviceOwnerApp(this.packageName)) {
                    dpm.setLockTaskPackages(adminName, arrayOf(this.packageName, packageName))
                }
            } catch (e: Exception) { e.printStackTrace() }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)

            try { startLockTask() } catch (e: Exception) { }
        }
    }

    private fun getInstalledApps(): List<AppDetailedInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(mainIntent, 0).map {
            AppDetailedInfo(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(packageManager).toString(),
                icon = it.loadIcon(packageManager)
            )
        }.sortedBy { it.label }
    }

    private fun exportConfig() {
        lifecycleScope.launch {
            try {
                val queue = db.appDao().getQueueSnapshot()
                val json = Gson().toJson(queue)
                val file = File(getExternalFilesDir(null), "ACL_CONFIG_${System.currentTimeMillis()}.json")
                file.writeText(json)
                Toast.makeText(this@MainActivity, "Exportado: ${file.name}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error al exportar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readJsonFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonContent = reader.readText()

                    val type = object : TypeToken<List<AppEntity>>() {}.type
                    val list: List<AppEntity> = Gson().fromJson(jsonContent, type)

                    // Limpiar y cargar nuevos datos
                    db.appDao().clearAll() // Asegúrate de tener este método en tu DAO
                    list.forEach {
                        // Insertamos como nuevos registros (id = 0) para evitar conflictos de PrimaryKey
                        db.appDao().insertApp(it.copy(id = 0))
                    }

                    Toast.makeText(this@MainActivity, "Importación exitosa", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun exitToSettings() {
        isKioskRunning = false
        try { stopLockTask() } catch (e: Exception) { }
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onResume() { super.onResume(); hideSystemUI() }
}