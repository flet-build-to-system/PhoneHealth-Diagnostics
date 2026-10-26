package com.example.data.model

enum class TestCategory(val displayName: String, val iconDescription: String) {
    SCREEN("Screen & Touch", "Display and digitizer diagnostics"),
    BATTERY("Battery Health", "Power, voltage and thermal state"),
    AUDIO("Speaker & Mic", "Stereo audio channels and microphone"),
    SENSORS("Sensors", "Motion, orientation and ambient sensors"),
    HAPTICS_TORCH("Haptics & Flash", "Vibration motors and camera torch"),
    SYSTEM_INFO("System & Memory", "Processor, RAM and storage health")
}

enum class TestStatus(val label: String) {
    UNTESTED("Not Tested"),
    PASSED("Passed"),
    WARNING("Warning"),
    FAILED("Failed"),
    SKIPPED("Skipped")
}

data class DiagnosticItem(
    val id: String,
    val title: String,
    val description: String,
    val category: TestCategory,
    val status: TestStatus = TestStatus.UNTESTED,
    val detailMessage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class BatterySnapshot(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val plugType: String = "Battery",
    val health: String = "Good",
    val temperatureCelsius: Float = 25f,
    val voltageMv: Int = 4000,
    val technology: String = "Li-ion",
    val powerStatus: String = "Discharging"
)

data class DeviceHardwareSpecs(
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val board: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val displayResolution: String = "",
    val displayRefreshRate: Float = 60f,
    val displayDensityDpi: Int = 0,
    val ramTotalGb: Double = 0.0,
    val ramAvailableGb: Double = 0.0,
    val storageTotalGb: Double = 0.0,
    val storageAvailableGb: Double = 0.0
)

data class OverallHealth(
    val score: Int = 100, // 0 - 100
    val rating: String = "Optimal",
    val passedCount: Int = 0,
    val failedCount: Int = 0,
    val warningCount: Int = 0,
    val totalCount: Int = 0
)
