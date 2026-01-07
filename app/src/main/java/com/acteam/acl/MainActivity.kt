package com.acteam.acl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
import androidx.activity.result.contract.ActivityResultContracts
import java.io.BufferedReader
import java.io.InputStreamReader

// Modelo para manejar iconos en la UI
data class AppDetailedInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

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

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "acl-db").build()
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
                        db.appDao().insertApp(AppEntity(
                            packageName = pkg,
                            label = label,
                            delaySeconds = seconds,
                            orderIndex = currentQueue.size,
                            runOnlyOnce = onlyOnce
                        ))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ACLScreen(
    appQueue: List<AppEntity>,
    availableApps: List<AppDetailedInfo>,
    onAddApp: (String, String, Int, Boolean) -> Unit,
    onRemoveApp: (Int) -> Unit,
    onExitKiosk: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onExport: () -> Unit,
    onImportRequest: () -> Unit,
    isPaused: Boolean,
    onTogglePause: (Boolean) -> Unit
) {
    var showAdmin by remember { mutableStateOf(false) }
    var secretClickCount by remember { mutableStateOf(0) }
    var selectedAppForConfig by remember { mutableStateOf<AppDetailedInfo?>(null) }
    var inputSeconds by remember { mutableStateOf("15") }
    var runOnlyOnce by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Botón Secreto
        Box(Modifier.size(100.dp).align(Alignment.TopEnd).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            secretClickCount++
            if (secretClickCount >= 5) {
                showAdmin = true
                onTogglePause(true) // Pausamos al entrar al menú
                secretClickCount = 0
            }
        })

        if (!showAdmin) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ACL Launcher", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(20.dp))
                if (appQueue.isEmpty()) {
                    Text("⚠️ Cola vacía", color = Color.Red, fontWeight = FontWeight.Bold)
                } else {
                    CircularProgressIndicator()
                    Text("Secuencia en curso...")
                }
            }
        } else {
            Surface(Modifier.fillMaxSize(), color = Color.White) {
                Column(Modifier.padding(16.dp)) {
                    Text("Configuración Técnica", style = MaterialTheme.typography.titleLarge)

                    Row(Modifier.weight(1f)) {
                        // Lista de Cola
                        LazyColumn(Modifier.weight(1f).padding(8.dp)) {
                            item { Text("Cola:", fontWeight = FontWeight.Bold) }
                            items(appQueue) { app ->
                                Card(Modifier.padding(vertical = 4.dp)) {
                                    Row(Modifier.padding(8.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Text("${app.label}\n${app.delaySeconds}s | Única: ${app.runOnlyOnce}")
                                        IconButton(onClick = { onRemoveApp(app.id) }) { Text("❌") }
                                    }
                                }
                            }
                        }
                        // Lista de Instaladas con Iconos
                        LazyColumn(Modifier.weight(1f).padding(8.dp)) {
                            item { Text("Instaladas:", fontWeight = FontWeight.Bold) }
                            items(availableApps) { app ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selectedAppForConfig = app }.padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = app.icon.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(app.label, maxLines = 1)
                                }
                            }
                        }
                    }

                    // Botones de Acción
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        Button(onClick = {
                            showAdmin = false
                            onTogglePause(false) // Reanudamos al salir
                        }) { Text("Aplicar") }
                        Button(onClick = onExport) { Text("Exp") }
                        Button(onClick = onImportRequest) { Text("Imp") }
                        Button(onClick = onExitKiosk, colors = ButtonDefaults.buttonColors(Color.Red)) { Text("Salir") }
                    }
                }
            }
        }

        if (selectedAppForConfig != null) {
            AlertDialog(
                onDismissRequest = { selectedAppForConfig = null },
                title = { Text("Configurar Lanzamiento") },
                text = {
                    Column {
                        TextField(value = inputSeconds, onValueChange = { inputSeconds = it }, label = { Text("Segundos") })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = runOnlyOnce, onCheckedChange = { runOnlyOnce = it })
                            Text("¿Ejecutar solo una vez?")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onAddApp(selectedAppForConfig!!.packageName, selectedAppForConfig!!.label, inputSeconds.toIntOrNull() ?: 15, runOnlyOnce)
                        selectedAppForConfig = null
                    }) { Text("Confirmar") }
                }
            )
        }
    }
}