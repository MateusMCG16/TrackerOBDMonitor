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
import br.com.tracker.obd.data.ConnectionPhase
import br.com.tracker.obd.data.DataGroup
import br.com.tracker.obd.ui.*
import br.com.tracker.obd.ui.theme.Primer
import br.com.tracker.obd.ui.theme.TrackerTheme
import br.com.tracker.obd.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {
    private val model: DashboardViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(1, 4, 9))
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
                var dataTopic by rememberSaveable { mutableStateOf(DataTopic.MOTOR) }
                var showConnection by rememberSaveable { mutableStateOf(false) }
                val redline by model.redline.collectAsStateWithLifecycle()
                // Asked once per launch, as soon as a real session delivers its first RPM
                // sample: earlier there is nothing to measure, and demo has no engine.
                var redlineAsked by rememberSaveable { mutableStateOf(false) }
                var showRedline by rememberSaveable { mutableStateOf(false) }
                val rpmArrived = state.phase == ConnectionPhase.RECEIVING && state.telemetry.rpm.reading.value != null
                LaunchedEffect(rpmArrived) {
                    if (rpmArrived && !redlineAsked) { redlineAsked = true; showRedline = true }
                }
                // Only the screen on display is read: Instruments keeps the link for speed and RPM.
                // While measuring the redline, nothing else may take the adapter's time.
                LaunchedEffect(tab, dataTopic, showRedline) {
                    model.focus(if (showRedline) emptySet() else when (tab) {
                        Tab.INSTRUMENTS -> emptySet()
                        Tab.DATA -> setOf(dataTopic.group)
                        Tab.DIAGNOSTICS -> setOf(DataGroup.HISTORY)
                        Tab.HELP -> setOf(DataGroup.VEHICLE)
                    })
                }
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
                Scaffold(containerColor = Primer.BgDefault, bottomBar = {
                    TrackerNavBar(tab,
                        alert = state.dtc.status?.milOn == true || state.dtc.codes.isNotEmpty(),
                        notice = state.warnings.isNotEmpty()) { tab = it }
                }) { inner ->
                    val fill = Modifier.padding(inner)
                    when (tab) {
                        Tab.INSTRUMENTS -> InstrumentsScreen(state, link, redline,
                            onConnect = { showConnection = true; refresh() },
                            onDemo = model::demo, onDisconnect = model::disconnect, modifier = fill)
                        Tab.DATA -> DataScreen(state, dataTopic, onTopic = { dataTopic = it }, modifier = fill)
                        Tab.DIAGNOSTICS -> DiagnosticsScreen(state, onRead = model::readDtc, modifier = fill)
                        Tab.HELP -> HelpScreen(state, redline, onRedline = { showRedline = true }, modifier = fill)
                    }
                    if (showRedline) RedlineScreen(state.telemetry.rpm.reading,
                        live = state.phase == ConnectionPhase.RECEIVING || state.phase == ConnectionPhase.WAITING,
                        saved = redline,
                        onSave = { model.saveRedline(it); showRedline = false; tab = Tab.INSTRUMENTS },
                        onDismiss = { showRedline = false })
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
