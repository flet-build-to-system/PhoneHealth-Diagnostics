package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DiagnosticReportEntity
import com.example.data.model.BatterySnapshot
import com.example.data.model.DeviceHardwareSpecs
import com.example.data.model.DiagnosticItem
import com.example.data.model.OverallHealth
import com.example.data.model.TestCategory
import com.example.data.model.TestStatus
import com.example.diagnostics.AudioTestEngine
import com.example.diagnostics.HardwareDiagnosticsManager
import com.example.diagnostics.SensorReadings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class AppScreen {
    HOME,
    SCREEN_TEST,
    BATTERY_TEST,
    AUDIO_TEST,
    SENSORS_TEST,
    HAPTICS_TORCH_TEST,
    REPORT_DETAIL,
    HISTORY
}

class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {

    val hardwareManager = HardwareDiagnosticsManager(application.applicationContext)
    val audioEngine = AudioTestEngine(viewModelScope)
    private val db = AppDatabase.getInstance(application)
    private val dao = db.diagnosticDao()

    val batteryState: StateFlow<BatterySnapshot> = hardwareManager.batteryState
    val sensorReadings: StateFlow<SensorReadings> = hardwareManager.sensorReadings
    val isTorchOn: StateFlow<Boolean> = hardwareManager.isTorchOn
    val hasTorch: StateFlow<Boolean> = hardwareManager.hasTorch

    private val _deviceSpecs = MutableStateFlow(hardwareManager.getDeviceHardwareSpecs())
    val deviceSpecs: StateFlow<DeviceHardwareSpecs> = _deviceSpecs.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Navigation history stack for back handling
    private val screenStack = mutableListOf<AppScreen>(AppScreen.HOME)

    private val _testItems = MutableStateFlow<List<DiagnosticItem>>(createDefaultTestItems())
    val testItems: StateFlow<List<DiagnosticItem>> = _testItems.asStateFlow()

    private val _lastSavedReportId = MutableStateFlow<Long?>(null)
    val lastSavedReportId: StateFlow<Long?> = _lastSavedReportId.asStateFlow()

    val savedReports: StateFlow<List<DiagnosticReportEntity>> = dao.getAllReportsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        hardwareManager.startSensorListening()
    }

    private fun createDefaultTestItems(): List<DiagnosticItem> {
        return listOf(
            DiagnosticItem("screen_dead_pixel", "Screen Pixels", "RGBW dead/stuck pixel visual check", TestCategory.SCREEN),
            DiagnosticItem("screen_multitouch", "Multi-Touch", "Simultaneous touch point recognition", TestCategory.SCREEN),
            DiagnosticItem("screen_digitizer", "Touch Digitizer", "Full display touch response grid", TestCategory.SCREEN),

            DiagnosticItem("battery_health", "Battery Health", "Cell condition and chemical health", TestCategory.BATTERY),
            DiagnosticItem("battery_temp", "Thermal Status", "Operating temperature safety threshold", TestCategory.BATTERY),
            DiagnosticItem("battery_voltage", "Voltage Output", "Consistent battery cell power output", TestCategory.BATTERY),

            DiagnosticItem("audio_stereo_l", "Left Speaker", "Left stereo channel frequency tone", TestCategory.AUDIO),
            DiagnosticItem("audio_stereo_r", "Right Speaker", "Right stereo channel frequency tone", TestCategory.AUDIO),
            DiagnosticItem("audio_freq_sweep", "Frequency Response", "Full acoustic sweep (200Hz - 4kHz)", TestCategory.AUDIO),
            DiagnosticItem("audio_microphone", "Microphone Sensitivity", "Audio input level and decibel response", TestCategory.AUDIO),

            DiagnosticItem("sensor_accel", "Accelerometer", "3-axis tilt and gravity motion sensing", TestCategory.SENSORS),
            DiagnosticItem("sensor_compass", "Compass / Magnetometer", "Magnetic orientation and field heading", TestCategory.SENSORS),
            DiagnosticItem("sensor_light", "Ambient Light", "Environment illumination measurement (Lux)", TestCategory.SENSORS),
            DiagnosticItem("sensor_prox", "Proximity Sensor", "Near/far ear detection", TestCategory.SENSORS),

            DiagnosticItem("haptics_motor", "Vibration Motor", "Haptic actuator click and wave patterns", TestCategory.HAPTICS_TORCH),
            DiagnosticItem("torch_light", "Flashlight / LED", "Rear camera flash emitter functionality", TestCategory.HAPTICS_TORCH),

            DiagnosticItem("system_ram", "RAM Memory", "Available operating memory threshold", TestCategory.SYSTEM_INFO),
            DiagnosticItem("system_storage", "Internal Storage", "Device internal storage capacity and health", TestCategory.SYSTEM_INFO)
        )
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            screenStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.lastIndex)
            _currentScreen.value = screenStack.last()
            return true
        }
        return false
    }

    fun updateTestResult(id: String, status: TestStatus, detailMessage: String = "") {
        _testItems.value = _testItems.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = status,
                    detailMessage = detailMessage.ifEmpty { status.label },
                    timestamp = System.currentTimeMillis()
                )
            } else {
                item
            }
        }
    }

    fun calculateOverallHealth(): OverallHealth {
        val items = _testItems.value
        val tested = items.filter { it.status != TestStatus.UNTESTED && it.status != TestStatus.SKIPPED }
        val passed = items.count { it.status == TestStatus.PASSED }
        val failed = items.count { it.status == TestStatus.FAILED }
        val warnings = items.count { it.status == TestStatus.WARNING }

        if (tested.isEmpty()) {
            return OverallHealth(
                score = 100,
                rating = "Untested",
                passedCount = 0,
                failedCount = 0,
                warningCount = 0,
                totalCount = items.size
            )
        }

        // Weighted penalty calculation: failed takes -25, warning takes -10
        val rawScore = (100 - (failed * 22) - (warnings * 8)).coerceIn(0, 100)
        val rating = when {
            rawScore >= 90 -> "Optimal Condition"
            rawScore >= 75 -> "Good Condition"
            rawScore >= 50 -> "Fair Condition"
            else -> "Needs Attention"
        }

        return OverallHealth(
            score = rawScore,
            rating = rating,
            passedCount = passed,
            failedCount = failed,
            warningCount = warnings,
            totalCount = items.size
        )
    }

    fun evaluateAutomaticSystemHealth() {
        val battery = batteryState.value
        val specs = deviceSpecs.value
        val sensors = sensorReadings.value

        // Battery health
        val isBattGood = battery.health.equals("Good", ignoreCase = true) || battery.health.equals("Normal", ignoreCase = true)
        updateTestResult(
            "battery_health",
            if (isBattGood) TestStatus.PASSED else TestStatus.WARNING,
            "${battery.health} (${battery.levelPercent}% ${battery.powerStatus})"
        )

        // Battery thermal
        val isTempOk = battery.temperatureCelsius in 10f..42f
        updateTestResult(
            "battery_temp",
            if (isTempOk) TestStatus.PASSED else if (battery.temperatureCelsius > 45f) TestStatus.FAILED else TestStatus.WARNING,
            "${battery.temperatureCelsius}°C (Normal operating range 15°C - 40°C)"
        )

        // Battery voltage
        val isVoltOk = battery.voltageMv in 3400..4500
        updateTestResult(
            "battery_voltage",
            if (isVoltOk) TestStatus.PASSED else TestStatus.WARNING,
            "${battery.voltageMv} mV (${battery.technology})"
        )

        // Sensors availability
        if (sensors.hasAccelerometer) {
            updateTestResult("sensor_accel", TestStatus.PASSED, "Active (3-axis functional)")
        }
        if (sensors.hasCompass) {
            updateTestResult("sensor_compass", TestStatus.PASSED, "Active (${sensors.compassDegrees.toInt()}° heading)")
        }
        if (sensors.hasLightSensor) {
            updateTestResult("sensor_light", TestStatus.PASSED, "Active (${sensors.ambientLux.toInt()} Lux detected)")
        }
        if (sensors.hasProximitySensor) {
            updateTestResult("sensor_prox", TestStatus.PASSED, "Active (${sensors.proximityCm} cm)")
        }

        // RAM & Storage
        val ramHealthy = specs.ramAvailableGb > 0.4
        updateTestResult(
            "system_ram",
            if (ramHealthy) TestStatus.PASSED else TestStatus.WARNING,
            "${specs.ramAvailableGb} GB free of ${specs.ramTotalGb} GB total"
        )

        val storageHealthy = specs.storageAvailableGb > 1.0
        updateTestResult(
            "system_storage",
            if (storageHealthy) TestStatus.PASSED else TestStatus.WARNING,
            "${specs.storageAvailableGb} GB free of ${specs.storageTotalGb} GB total"
        )
    }

    fun saveReportToHistory(notes: String = "") {
        viewModelScope.launch {
            val health = calculateOverallHealth()
            val battery = batteryState.value
            val specs = deviceSpecs.value

            val jsonArray = JSONArray()
            _testItems.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("category", item.category.name)
                    put("status", item.status.name)
                    put("detail", item.detailMessage)
                }
                jsonArray.put(obj)
            }

            val entity = DiagnosticReportEntity(
                healthScore = health.score,
                rating = health.rating,
                deviceModel = "${specs.manufacturer} ${specs.model}",
                androidVersion = "Android ${specs.androidVersion} (API ${specs.sdkInt})",
                batteryHealth = battery.health,
                batteryLevel = battery.levelPercent,
                batteryTempC = battery.temperatureCelsius,
                passedCount = health.passedCount,
                failedCount = health.failedCount,
                warningCount = health.warningCount,
                totalCount = health.totalCount,
                testSummaryJson = jsonArray.toString(),
                notes = notes
            )

            val newId = dao.insertReport(entity)
            _lastSavedReportId.value = newId
        }
    }

    fun deleteReport(id: Long) {
        viewModelScope.launch {
            dao.deleteReport(id)
        }
    }

    fun resetAllTests() {
        _testItems.value = createDefaultTestItems()
        _lastSavedReportId.value = null
    }

    fun generateShareableSummary(): String {
        val health = calculateOverallHealth()
        val specs = deviceSpecs.value
        val battery = batteryState.value

        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("📱 PHONE HARDWARE DIAGNOSTIC REPORT")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("Device: ${specs.manufacturer} ${specs.model} (${specs.device})")
        sb.appendLine("OS: Android ${specs.androidVersion} (API ${specs.sdkInt})")
        sb.appendLine("Screen: ${specs.displayResolution} @ ${specs.displayRefreshRate}Hz")
        sb.appendLine("RAM: ${specs.ramAvailableGb} GB / ${specs.ramTotalGb} GB")
        sb.appendLine("Storage: ${specs.storageAvailableGb} GB / ${specs.storageTotalGb} GB")
        sb.appendLine("Battery: ${battery.levelPercent}% (${battery.health}, ${battery.temperatureCelsius}°C, ${battery.voltageMv}mV)")
        sb.appendLine("-----------------------------------")
        sb.appendLine("HEALTH SCORE: ${health.score}/100 [${health.rating.uppercase()}]")
        sb.appendLine("Passed: ${health.passedCount} | Failed: ${health.failedCount} | Warnings: ${health.warningCount}")
        sb.appendLine("-----------------------------------")
        sb.appendLine("COMPONENT TEST BREAKDOWN:")

        TestCategory.entries.forEach { cat ->
            val catItems = _testItems.value.filter { it.category == cat }
            if (catItems.isNotEmpty()) {
                sb.appendLine("\n[${cat.displayName}]")
                catItems.forEach { item ->
                    val mark = when (item.status) {
                        TestStatus.PASSED -> "✅ [PASS]"
                        TestStatus.FAILED -> "❌ [FAIL]"
                        TestStatus.WARNING -> "⚠️ [WARN]"
                        TestStatus.SKIPPED -> "⏭️ [SKIP]"
                        TestStatus.UNTESTED -> "⚪ [NOT TESTED]"
                    }
                    sb.appendLine("  $mark ${item.title}: ${item.detailMessage.ifEmpty { item.status.label }}")
                }
            }
        }

        sb.appendLine("\nGenerated via Phone Diagnostics App")
        sb.appendLine("═══════════════════════════════════")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        hardwareManager.cleanUp()
        audioEngine.releaseAll()
    }
}
