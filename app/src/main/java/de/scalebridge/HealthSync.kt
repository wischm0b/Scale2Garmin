package de.scalebridge

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** One-way, write-only export. Each destination keeps its own acknowledgement. */
object HealthSync {
    private val running = AtomicBoolean(false)
    private val types = listOf(WeightRecord::class, BodyFatRecord::class, BodyWaterMassRecord::class,
        BoneMassRecord::class, BasalMetabolicRateRecord::class)
    @JvmStatic fun permissions(): Set<String> = types.map { HealthPermission.getWritePermission(it) }.toSet()
    @JvmStatic fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    @JvmStatic fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    data class Export(val bit: Int, val record: Record)

    /** Percentage muscle is not lean body mass; do not invent an equivalent HC record. */
    @JvmStatic fun records(m: Measurement, granted: Set<String>, sentMask: Int): List<Export> {
        val time = Instant.ofEpochSecond(m.unixSeconds)
        val zone = ZoneId.systemDefault().rules.getOffset(time)
        val result = mutableListOf<Export>()
        fun add(index: Int, valid: Boolean, build: (Metadata) -> Record) {
            val bit = 1 shl index
            if (valid && sentMask and bit == 0 && HealthPermission.getWritePermission(types[index]) in granted) {
                // Stable per-measurement/type IDs make retry after a lost response idempotent.
                val metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_SCALE),
                    clientRecordId = "scalebridge:${m.id()}:$index", clientRecordVersion = 1)
                result += Export(bit, build(metadata))
            }
        }
        val weightValid = m.weight.isFinite() && m.weight > 0 && m.weight <= 1000
        add(0, weightValid) { WeightRecord(time, zone, Mass.kilograms(m.weight), it) }
        add(1, m.fat.isFinite() && m.fat in 0.0..100.0) { BodyFatRecord(time, zone, Percentage(m.fat), it) }
        add(2, weightValid && m.water.isFinite() && m.water in 0.0..100.0) {
            BodyWaterMassRecord(time, zone, Mass.kilograms(m.weight * m.water / 100.0), it)
        }
        add(3, m.bone.isFinite() && m.bone > 0 && m.bone <= m.weight) { BoneMassRecord(time, zone, Mass.kilograms(m.bone), it) }
        add(4, m.basal.isFinite() && m.basal > 0 && m.basal <= 10000) {
            BasalMetabolicRateRecord(time, zone, Power.kilocaloriesPerDay(m.basal), it)
        }
        return result
    }

    @JvmStatic fun status(context: Context, message: String) {
        // Store untranslated text so changing the app language also changes the status.
        context.getSharedPreferences("settings", 0).edit().putString("healthStatus", message).apply()
        context.sendBroadcast(Intent(Sync.EVENT).setPackage(context.packageName))
    }

    /** Called only on the job's worker thread. Returns whether a transient failure needs retry. */
    @JvmStatic fun run(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", 0)
        if (!prefs.getBoolean("healthSync", false) || !running.compareAndSet(false, true)) return false
        val user = prefs.getInt("user", -1)
        try {
            if (user !in 1..8) return false
            if (availability(context) != HealthConnectClient.SDK_AVAILABLE) {
                status(context, "Health Connect ist nicht verfügbar. Bitte installieren oder aktualisieren.")
                return false
            }
            return runBlocking {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions().intersect(permissions())
                if (granted.isEmpty()) {
                    status(context, "Health Connect benötigt eine Schreibberechtigung.")
                    return@runBlocking false
                }
                status(context, "Übertragung zu Health Connect …")
                Store(context).use { db ->
                    for (row in db.timeline(user, 0, Instant.now().epochSecond)) {
                        if (Thread.currentThread().isInterrupted || !prefs.getBoolean("healthSync", false) || prefs.getInt("user", -1) != user) return@runBlocking false
                        val m = row.measurement()
                        val export = records(m, granted, db.healthMask(m.id()))
                        if (export.isEmpty()) continue
                        // Re-check before every write: permission may be revoked during a history export.
                        if (!client.permissionController.getGrantedPermissions().containsAll(granted)) throw SecurityException()
                        client.insertRecords(export.map { it.record })
                        db.healthSent(m.id(), export.fold(0) { mask, item -> mask or item.bit })
                    }
                }
                status(context, if (granted.containsAll(permissions())) "Health Connect ist auf dem aktuellen Stand." else "Health Connect ist aktuell. Es werden nur freigegebene Messwerte übertragen.")
                false
            }
        } catch (_: SecurityException) {
            status(context, "Health Connect benötigt eine Schreibberechtigung.")
            return false
        } catch (_: IllegalArgumentException) {
            status(context, "Ein Messwert konnte nicht übertragen werden. Bitte die Messdaten prüfen.")
            return false
        } catch (_: Exception) {
            status(context, "Health Connect ist vorübergehend nicht erreichbar. Die Übertragung wird wiederholt.")
            return true
        } finally { running.set(false) }
    }
}
