package my.toolmentum.hundredlife

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthLogicTest {

    @Test
    fun sugarValidation_acceptsBoundsForBothUnits() {
        assertNull(GlucoseUnits.sugarError("1.0", GlucoseUnits.MMOL_L))
        assertNull(GlucoseUnits.sugarError("33.0", GlucoseUnits.MMOL_L))
        assertNull(GlucoseUnits.sugarError("6.5", GlucoseUnits.MMOL_L))
        assertEquals(
            "Must be between 1.0 and 33.0 mmol/L",
            GlucoseUnits.sugarError("0.9", GlucoseUnits.MMOL_L)
        )
        assertEquals(
            "Must be between 1.0 and 33.0 mmol/L",
            GlucoseUnits.sugarError("33.1", GlucoseUnits.MMOL_L)
        )

        assertNull(GlucoseUnits.sugarError("18", GlucoseUnits.MG_DL))
        assertNull(GlucoseUnits.sugarError("600", GlucoseUnits.MG_DL))
        assertNull(GlucoseUnits.sugarError("120", GlucoseUnits.MG_DL))
        assertEquals(
            "Must be between 18 and 600 mg/dL",
            GlucoseUnits.sugarError("17", GlucoseUnits.MG_DL)
        )
        assertEquals(
            "Must be between 18 and 600 mg/dL",
            GlucoseUnits.sugarError("601", GlucoseUnits.MG_DL)
        )
        assertEquals("Please enter a number", GlucoseUnits.sugarError("abc", GlucoseUnits.MMOL_L))
        assertEquals("Unknown glucose unit", GlucoseUnits.sugarError("6.5", "banana"))
    }

    @Test
    fun stepsValidation_rejectsFractionsAndOutOfRange() {
        assertNull(GlucoseUnits.stepsError("0"))
        assertNull(GlucoseUnits.stepsError("100000"))
        assertEquals("Please enter a whole number", GlucoseUnits.stepsError("5.5"))
        assertEquals("Must be between 0 and 100,000", GlucoseUnits.stepsError("-1"))
        assertEquals("Must be between 0 and 100,000", GlucoseUnits.stepsError("100001"))
    }

    @Test
    fun unitConversion_andHbA1cFormula() {
        assertEquals(99.0, GlucoseUnits.toMgDl(5.5, GlucoseUnits.MMOL_L), 0.0001)
        assertEquals(154.0, GlucoseUnits.toMgDl(154.0, GlucoseUnits.MG_DL), 0.0001)
        val hba1c = GlucoseUnits.estimateHbA1cPercent(154.0)
        assertEquals((154.0 + 46.7) / 28.7, hba1c, 0.0001)
        assertEquals(7.0, hba1c, 0.02)
    }

    @Test
    fun exportFileName_hasNoIcNameOrReadings() {
        val ic = "940720106883"
        val file = exportReportFileName("November", 2026)
        assertEquals("100Life_report_November_2026.csv", file)
        assertFalse(file.contains(ic))
        assertFalse(file.contains("6.5"))
        assertFalse(file.contains("Yugen"))
        assertFalse(file.contains("/"))
        assertFalse(file.contains(".."))

        val fromIcShapedMonth = exportReportFileName(ic, 2026)
        assertEquals("100Life_report_Month_2026.csv", fromIcShapedMonth)
        assertFalse(fromIcShapedMonth.contains(ic))
    }

    @Test
    fun exportSubject_omitsIcAndReadings() {
        val ic = "940720106883"
        val subject = exportReportSubject("A. Rahman $ic", "June", 2026)
        assertFalse(subject.contains(ic))
        assertFalse(subject.contains("IC"))
        assertFalse(subject.contains("mmol"))
        assertFalse(subject.contains("6.5"))
        assertTrue(subject.contains("June"))
        assertTrue(subject.contains("2026"))
        assertTrue(subject.contains("A. Rahman"))
        assertFalse(exportReportSubject("A\nB", "June", 2026).contains("\n"))
    }

    @Test
    fun plaintextSqliteHeader_detectedOnlyForRealMagic() {
        assertTrue(SqliteFileFormat.isPlaintextSqlite(SqliteFileFormat.HEADER))
        val random = ByteArray(SqliteFileFormat.HEADER.size) { 0x7f }
        assertFalse(SqliteFileFormat.isPlaintextSqlite(random))
        assertFalse(SqliteFileFormat.isPlaintextSqlite(ByteArray(4)))
    }

    @Test
    fun migrations_areAdditive() {
        val sql = HealthDbMigrations.upgradeStatements(1, 3)
        assertEquals(3, sql.size)
        assertTrue(sql.all { it.startsWith("ALTER TABLE entries ADD COLUMN") })
        assertTrue(sql.none { it.contains("DROP", ignoreCase = true) })
        assertTrue(HealthDbMigrations.upgradeStatements(2, 3).none { it.contains("DROP", ignoreCase = true) })
        assertTrue(HealthDbMigrations.upgradeStatements(3, 3).isEmpty())
        assertFalse(HealthDbMigrations.CREATE_ENTRIES_SQL.contains("DROP", ignoreCase = true))
    }

    @Test
    fun migrations_refuseUnknownFutureVersionAndDowngrade() {
        assertThrows(IllegalStateException::class.java) {
            HealthDbMigrations.upgradeStatements(3, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HealthDbMigrations.upgradeStatements(3, 2)
        }
    }
}
