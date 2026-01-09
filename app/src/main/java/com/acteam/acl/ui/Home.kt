package com.acteam.acl.ui

import android.graphics.drawable.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.acteam.acl.R
import com.acteam.acl.models.AppDetailedInfo
import com.acteam.acl.models.AppEntity
import com.acteam.acl.utils.getDateTimeNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val fontAnta = FontFamily(
        Font(R.font.antaregular, FontWeight.Normal),
    )

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
    var showMenu by remember { mutableStateOf(false) }
    var secretClickCount by remember { mutableStateOf(0) }
    var selectedAppForConfig by remember { mutableStateOf<AppDetailedInfo?>(null) }
    var inputSeconds by remember { mutableStateOf("15") }
    var runOnlyOnce by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(18, 17, 17))) {
        // Botón Secreto
        Box(
            Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    secretClickCount++
                    if (secretClickCount >= 2) {
                        showAdmin = true
                        onTogglePause(true) // Pausamos al entrar al menú
                        secretClickCount = 0
                    }
                })

        if (!showAdmin) {
            ColumnClockLogo()
        } else {
            AdminPanel(
                appQueue = appQueue,
                availableApps = availableApps,
                showMenu = showMenu,
                onRemoveApp = onRemoveApp,
                onExport = onExport,
                onImportRequest = onImportRequest,
                onExitKiosk = onExitKiosk,
                onCloseAdmin = {
                    showAdmin = false
                    onTogglePause(false)
                },
                onSelectApp = { app ->
                    selectedAppForConfig = app
                },
                onMoreOptions = {
                    showMenu = !showMenu
                },
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            )
        }

        if (selectedAppForConfig != null) {
            AlertDialog(
                onDismissRequest = { selectedAppForConfig = null },
                title = { Text("Configurar Lanzamiento") },
                text = {
                    Column {
                        TextField(
                            value = inputSeconds,
                            onValueChange = { inputSeconds = it },
                            label = { Text("Segundos") })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(45,168,255)),
                                checked = runOnlyOnce,
                                onCheckedChange = { runOnlyOnce = it })
                            Text("¿Ejecutar solo una vez?")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(45,168,255)),
                        onClick = {
                        onAddApp(
                            selectedAppForConfig!!.packageName,
                            selectedAppForConfig!!.label,
                            inputSeconds.toIntOrNull() ?: 15,
                            runOnlyOnce
                        )
                        selectedAppForConfig = null
                    }) { Text("Confirmar") }
                }
            )
        }
    }
}

@Composable
@Preview
fun Clock() {
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            time = getDateTimeNow()[0]
            date = getDateTimeNow()[1]
            delay(1000)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = time,
                fontFamily = fontAnta,
                color = Color.White,
                fontSize = 180.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = date,
                fontFamily = fontAnta,
                color = Color.White,
                fontSize = 40.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
@Preview
fun LogoACL() {
    Box(
        Modifier.padding(horizontal = 180.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 0.dp, vertical = 0.dp)
                .border(
                    width = 10.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(100.dp)
                )
        ) {
            Text(
                text = "ACL",
                fontFamily = fontAnta,
                color = Color.White,
                fontSize = 120.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
@Preview(widthDp = 800, heightDp = 1280)
fun ColumnClockLogo(appQueue: List<AppEntity> = emptyList()) {
    Column(

        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(25.dp))
        Clock()
        Spacer(Modifier.height(100.dp))
        LogoACL()
        if (appQueue.isEmpty()) {
            Text("⚠️ Cola vacía", color = Color.Red, fontWeight = FontWeight.Bold)
        } else {
            CircularProgressIndicator()
            Text("Secuencia en curso...")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun AdminPanel(
    appQueue: List<AppEntity> = emptyList(),
    availableApps: List<AppDetailedInfo> = emptyList(),
    showMenu: Boolean = false,
    onRemoveApp: (Int) -> Unit = {},
    onExitKiosk: () -> Unit = {},
    onExport: () -> Unit = {},
    onImportRequest: () -> Unit = {},
    onCloseAdmin: () -> Unit = {},
    onMoreOptions: () -> Unit = {},
    onSelectApp: (AppDetailedInfo) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {}
) {
    Scaffold(Modifier.fillMaxSize(), topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color(18, 17, 17)),
            title = { Text("Panel de Administración", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)},
            navigationIcon = {
                IconButton(onClick = onCloseAdmin) {
                    Icon(
                        tint = Color.White,
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Ir atrás"
                    )
                }
            },
            actions = {
                IconButton(onClick = onMoreOptions) {
                    Icon(
                        tint = Color.White,
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones"
                    )
                }
                DropdownMenu(
                    modifier = Modifier.width(200.dp),
                    expanded = showMenu,
                    onDismissRequest = {
                        onMoreOptions()
                    }
                ) {
                    DropdownMenuItem(
                        text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Accesibilidad")
                            Spacer(Modifier.width(10.dp))
                            Text("Ir a Accesibilidad")
                        }

                        },
                        onClick = onOpenAccessibilitySettings
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Exportar")
                            Spacer(Modifier.width(10.dp))
                            Text("Exportar Cola")
                            }
                        },
                        onClick = onExport
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Create,
                                    contentDescription = "Importar"
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Importar Cola")
                            }
                                                                                },
                        onClick = onImportRequest
                    )
                    Divider(
                        color = Color.Black,
                        thickness = 1.dp
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Salir")
                            Spacer(Modifier.width(10.dp))
                            Text("Salir de ACL") }
                                                                                },
                        onClick = onExitKiosk
                    )
                }
            }
        )
    },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCloseAdmin,
                containerColor = Color(18, 17, 17),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()){
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color(18, 17, 17),
            ) { }
        },
        containerColor = Color.White) {
        Column(
            Modifier
                .padding(it)
                ) {
            Row(Modifier.background(Color(18, 17, 17)).weight(1f)) {
                // Lista de Cola
                LazyColumn(Modifier
                    .weight(1f)
                    .padding(8.dp)) {
                    item { Text("Cola:", fontSize = 20.sp, color=Color.White, fontWeight = FontWeight.Bold) }
                    items(appQueue) { app ->
                        Card(Modifier.padding(vertical = 4.dp)) {
                            Row(
                                Modifier
                                    .background(Color.White)
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Text("${app.label}\n${app.delaySeconds}s | Única: ${app.runOnlyOnce}", color = Color.Black, fontSize = 18.sp)
                                IconButton(onClick = { onRemoveApp(app.id) }) { Text("❌") }
                            }
                        }
                    }
                }

                Divider(
                    Modifier.width(3.dp).fillMaxHeight(),
                    color = Color.Black,
                )

                // Lista de Instaladas con Iconos
                LazyColumn(Modifier
                    .background(Color.White)
                    .weight(1f)
                    .padding(8.dp)) {
                    item { Text("Apps Instaladas:", fontSize = 20.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
                    items(availableApps) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app) }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap().asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(app.label, color = Color.Black, fontSize = 20.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Botones de Acción
            /*Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        color = Color(18, 17, 17)
                    ),
                contentAlignment = Alignment.Center,
            ){
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        onClick = { onCloseAdmin() }) {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(50.dp)
                                .background(Color(96,255,64,),RoundedCornerShape(10.dp))
                                .border(
                                width = 2.dp,
                                color = Color.Green, shape = RoundedCornerShape(10.dp)),

                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Done, contentDescription = null, tint = Color(36, 71, 0))
                                Text("Aplicar", fontWeight = FontWeight.Bold, color = Color(36, 71, 0), fontSize = 18.sp)
                            }
                        }
                    }
                    *//*Button(onClick = onExport) { Text("Exp") }
                    Button(onClick = onImportRequest) { Text("Imp") }*//*
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .padding(horizontal = 10.dp),
                        onClick = { onExitKiosk() }) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(50.dp)
                                .background(Color(255, 38, 18),RoundedCornerShape(10.dp))
                                .border(
                                    width = 2.dp,
                                    color = Color.Red, shape = RoundedCornerShape(10.dp)),

                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.ExitToApp, contentDescription = null, tint = Color(80, 29, 16))
                                Text("Cerrar ACL", fontWeight = FontWeight.Bold, color = Color(80, 29, 16), fontSize = 18.sp)
                            }
                        }
                    }
                }
            }*/
        }
    }
}
