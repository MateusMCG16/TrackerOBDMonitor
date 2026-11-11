package br.com.tracker.obd.settings

import android.content.Context
import androidx.core.content.edit
import br.com.tracker.obd.data.RedlineSetting
import br.com.tracker.obd.data.RedlineSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The redline is the one thing the app keeps between launches: the car cannot be
 * asked for it. Two plain preferences, no telemetry; backups already exclude them.
 */
class RedlineStore(context: Context) {
    private val prefs = context.getSharedPreferences("tobd_settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val setting: StateFlow<RedlineSetting?> = mutable.asStateFlow()

    private fun load(): RedlineSetting? {
        val rpm = prefs.getInt(KEY_RPM, 0).takeIf { it > 0 } ?: return null
        val source = RedlineSource.entries.firstOrNull { it.name == prefs.getString(KEY_SOURCE, null) }
            ?: RedlineSource.TYPED
        return RedlineSetting(rpm, source)
    }

    fun save(setting: RedlineSetting) {
        prefs.edit { putInt(KEY_RPM, setting.rpm); putString(KEY_SOURCE, setting.source.name) }
        mutable.value = setting
    }

    private companion object {
        const val KEY_RPM = "redline_rpm"
        const val KEY_SOURCE = "redline_source"
    }
}
