package com.weightkit.app

import com.weightkit.app.model.Catalog
import com.weightkit.app.model.Clock
import com.weightkit.app.model.KeyValueStore
import java.io.File

const val HOUR = 3600_000L
const val DAY = 24 * HOUR

/** Injectable clock: [wall] can be set back like the device date; [mono] only moves forward. */
class FakeClock(var wall: Long = 1_760_000_000_000L, var mono: Long = 1_000_000L) : Clock {
    override fun wallMillis() = wall
    override fun monoMillis() = mono
    /** Real time passes (both clocks move). */
    fun advance(ms: Long) { wall += ms; mono += ms }
}

class MemStore : KeyValueStore {
    val map = HashMap<String, Any>()
    override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
}

/** The real bundled data (unit tests run with the app module as working directory). */
val catalog: Catalog by lazy {
    Catalog(File("src/main/assets/materials.json").readText(), File("src/main/assets/sections.json").readText())
}
