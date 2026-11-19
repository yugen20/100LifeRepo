package my.toolmentum.hundredlife

/**
 * Pure health, export, and schema rules. No Android APIs, so JVM unit tests
 * cover the same code the UI calls.
 */
object SettingKeys {
    const val PATIENT_NAME = "patient_name"
    const val PATIENT_IC = "patient_ic"
    const val DOCTOR_NAME = "doctor_name"
    const val DOCTOR_EMAIL = "doctor_email"
    const val GLUCOSE_UNIT = "glucose_unit"
    const val REMINDER_ENABLED = "reminder_enabled"
    const val REMINDER_HOUR = "reminder_hour"
    const val REMINDER_MINUTE = "reminder_minute"
}

object GlucoseUnits {
    const val MMOL_L = "mmol/L"
    const val MG_DL = "mg/dL"
    const val MMOL_TO_MGDL = 18.0

    fun sugarError(raw: String, unit: String): String? {
        val sugar = raw.trim().toDoubleOrNull()
            ?: return "Please enter a number"
        return when (unit) {
            MMOL_L -> if (sugar < 1.0 || sugar > 33.0) "Must be between 1.0 and 33.0 mmol/L" else null
            MG_DL -> if (sugar < 18.0 || sugar > 600.0) "Must be between 18 and 600 mg/dL" else null
            else -> "Unknown glucose unit"
        }
    }

    fun stepsError(raw: String): String? {
        val steps = raw.trim().toIntOrNull()
            ?: return "Please enter a whole number"
        return if (steps < 0 || steps > 100_000) "Must be between 0 and 100,000" else null
    }

    fun toMgDl(value: Double, unit: String): Double = when (unit) {
        MMOL_L -> value * MMOL_TO_MGDL
        MG_DL -> value
        else -> throw IllegalArgumentException("Unknown glucose unit: $unit")
    }

    /** ADAG formula: HbA1c (%) = (average mg/dL + 46.7) / 28.7 */
    fun estimateHbA1cPercent(averageMgDl: Double): Double = (averageMgDl + 46.7) / 28.7
}

fun monthCodeToName(code: String): String {
    return mapOf(
        "jan" to "January", "feb" to "February", "mar" to "March",
        "apr" to "April", "may" to "May", "jun" to "June",
        "jul" to "July", "aug" to "August", "sep" to "September",
        "oct" to "October", "nov" to "November", "dec" to "December"
    )[code] ?: code
}

/**
 * Attachment name for a doctor export. Month letters and the year only —
 * no patient name, IC, or readings, so the name is safe to show in an
 * email client or notification.
 */
fun exportReportFileName(monthName: String, year: Int): String {
    val month = monthName.filter { it.isLetter() }.ifEmpty { "Month" }
    return "100Life_report_${month}_$year.csv"
}

/**
 * Email subject. Includes the patient name so the doctor can see who wrote,
 * and never the IC or glucose/step values. Digit runs of 6+ are removed from
 * the name so an IC typed into the name field is not copied into the subject.
 */
fun exportReportSubject(patientName: String, monthName: String, year: Int): String {
    val name = patientName
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\d{6,}"), "")
        .trim()
        .ifEmpty { "Patient" }
    return "[100Life] $name | Monthly report $monthName $year"
}

object SqliteFileFormat {
    val HEADER: ByteArray = "SQLite format 3\u0000".encodeToByteArray()

    fun isPlaintextSqlite(header: ByteArray): Boolean {
        if (header.size < HEADER.size) return false
        for (i in HEADER.indices) {
            if (header[i] != HEADER[i]) return false
        }
        return true
    }
}

object HealthDbMigrations {
    const val VERSION = 3

    val CREATE_ENTRIES_SQL: String = """
        CREATE TABLE entries (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            month        TEXT NOT NULL,
            date         TEXT NOT NULL,
            sugar        REAL NOT NULL,
            steps        INTEGER NOT NULL,
            reading_type TEXT NOT NULL DEFAULT 'Fasting',
            notes        TEXT NOT NULL DEFAULT '',
            year         INTEGER NOT NULL DEFAULT 2026
        )
    """.trimIndent()

    /**
     * Incremental upgrades only. Versions 1 and 2 (pre-release plaintext
     * databases) had id, month, date, sugar, steps. Version 3 added
     * reading_type, notes, and year together. This release creates encrypted
     * databases at [VERSION] and does not run these statements for a brand
     * new file. Later versions must append a step here instead of dropping
     * the table.
     */
    fun upgradeStatements(oldVersion: Int, newVersion: Int): List<String> {
        require(oldVersion >= 1) { "New databases are created at version $VERSION" }
        require(newVersion >= oldVersion) { "Downgrades are refused, not migrated" }
        val statements = mutableListOf<String>()
        var version = oldVersion
        if (version < 3 && newVersion >= 3) {
            statements += "ALTER TABLE entries ADD COLUMN reading_type TEXT NOT NULL DEFAULT 'Fasting'"
            statements += "ALTER TABLE entries ADD COLUMN notes TEXT NOT NULL DEFAULT ''"
            statements += "ALTER TABLE entries ADD COLUMN year INTEGER NOT NULL DEFAULT 2026"
            version = 3
        }
        if (version != newVersion) {
            throw IllegalStateException(
                "No non-destructive migration from schema $version to $newVersion"
            )
        }
        return statements
    }
}
