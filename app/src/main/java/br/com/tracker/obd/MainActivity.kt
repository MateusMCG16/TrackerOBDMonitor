package br.com.tracker.obd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.tracker.obd.ui.*
import br.com.tracker.obd.ui.theme.Graphite
import br.com.tracker.obd.ui.theme.TrackerTheme
import br.com.tracker.obd.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {
    private val model: DashboardViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(16, 26, 40))
        )
        setContent {
            TrackerTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val link by model.bluetoothLink.collectAsStateWithLifecycle()
                val devices by model.devices.collectAsStateWithLifecycle()
                val error by model.deviceError.collectAsStateWithLifecycle()
                // Saveable: the session survives rotation in the ViewModel, so the
                // destination the user was looking at should survive it too.
                var tab by rememberSaveable { mutableStateOf(Tab.INSTRUMENTS) }
                var showConnection by rememberSaveable { mutableStateOf(false) }
                val context = LocalContext.current
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) model.refreshDevices() else model.permissionDenied()
                }
                val refresh: () -> Unit = {
                    if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else model.refreshDevices()
                }
                DisposableEffect(state.active) {
                    if (state.active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                BackHandler(enabled = tab != Tab.INSTRUMENTS) { tab = Tab.INSTRUMENTS }
                Scaffold(containerColor = Graphite, bottomBar = {
                    TrackerNavBar(tab,
                        alert = state.dtc.status?.milOn == true || state.dtc.codes.isNotEmpty(),
                        notice = state.warnings.isNotEmpty()) { tab = it }
                }) { inner ->
                    val fill = Modifier.padding(inner)
                    when (tab) {
                        Tab.INSTRUMENTS -> InstrumentsScreen(state, link,
                            onConnect = { showConnection = true; refresh() },
                            onDemo = model::demo, onDisconnect = model::disconnect, modifier = fill)
                        Tab.DIAGNOSTICS -> DiagnosticsScreen(state, onRead = model::readDtc, modifier = fill)
                        Tab.HELP -> HelpScreen(state, modifier = fill)
                    }
                    if (showConnection) ConnectionScreen(devices, error,
                        onSelect = { showConnection = false; model.connect(it) }, onRefresh = refresh,
                        onSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        onDismiss = { showConnection = false })
                }
            }
        }
    }
    override fun onStop() {
        if (!isChangingConfigurations) model.pause()
        super.onStop()
    }
}
