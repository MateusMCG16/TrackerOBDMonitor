package br.com.tracker.obd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.tracker.obd.data.ConnectionPhase
import br.com.tracker.obd.ui.*
import br.com.tracker.obd.ui.theme.TrackerTheme
import br.com.tracker.obd.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {
    private val model: DashboardViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(11, 15, 23))
        )
        setContent {
            TrackerTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val link by model.bluetoothLink.collectAsStateWithLifecycle()
                val devices by model.devices.collectAsStateWithLifecycle()
                val error by model.deviceError.collectAsStateWithLifecycle()
                var showConnection by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) model.refreshDevices() else model.permissionDenied()
                }
                val refresh: () -> Unit = {
                    if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else model.refreshDevices()
                }
                DisposableEffect(state.phase) {
                    if (state.phase !in setOf(ConnectionPhase.DISCONNECTED, ConnectionPhase.ERROR))
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                Surface {
                    DashboardScreen(state, link, onConnect = { showConnection = true; refresh() },
                        onDemo = model::demo, onDisconnect = model::disconnect)
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
