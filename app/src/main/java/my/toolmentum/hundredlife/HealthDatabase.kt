package my.toolmentum.hundredlife

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File
import java.security.SecureRandom

/**
 * SQLCipher database for glucose and step entries.
 *
 * The passphrase is 32 random bytes, Base64-encoded so it is valid UTF-8 with
 * no zero bytes (what SQLCipher expects for a passphrase). Those bytes are
 * stored only after being encrypted by an Android Keystore AES-GCM key, in
 * [Context.getNoBackupFilesDir]. The keystore key itself is not exportable.
 *
 * A leftover plaintext `health.db` from development builds is deleted and
 * created again empty. There are no production installs to migrate. An
 * encrypted file is never deleted because it cannot be opened.
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    openPassphrase(context.applicationContext),
    null,
    HealthDbMigrations.VERSION,
    0,
    null,
    null,
    false
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(HealthDbMigrations.CREATE_ENTRIES_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (statement in HealthDbMigrations.upgradeStatements(oldVersion, newVersion)) {
            db.execSQL(statement)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw android.database.sqlite.SQLiteException(
            "Refusing to downgrade health.db from $oldVersion to $newVersion"
        )
    }

    fun insertEntry(
        month: String,
        date: String,
        sugar: Double,
        steps: Int,
        readingType: String,
        notes: String,
        year: Int
    ) {
        val values = ContentValues().apply {
            put("month", month)
            put("date", date)
            put("sugar", sugar)
            put("steps", steps)
            put("reading_type", readingType)
            put("notes", notes)
            put("year", year)
        }
        writableDatabase.insert("entries", null, values)
    }

    fun deleteEntry(id: Int) {
        writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
    }

    fun getEntries(month: String, year: Int): List<Entry> = query(
        "SELECT * FROM entries WHERE month = ? AND year = ? ORDER BY date DESC",
        arrayOf(month, year.toString())
    )

    fun getLast5Entries(month: String, year: Int): List<Entry> = query(
        "SELECT * FROM entries WHERE month = ? AND year = ? ORDER BY date DESC LIMIT 5",
        arrayOf(month, year.toString())
    )

    fun getAllEntriesForYear(year: Int): List<Entry> = query(
        "SELECT * FROM entries WHERE year = ? ORDER BY date DESC",
        arrayOf(year.toString())
    )

    private fun query(sql: String, args: Array<String>): List<Entry> {
        val cursor = readableDatabase.rawQuery(sql, args)
        cursor.use { rows ->
            val entries = mutableListOf<Entry>()
            while (rows.moveToNext()) entries.add(cursorToEntry(rows))
            return entries
        }
    }

    private fun cursorToEntry(cursor: Cursor): Entry {
        return Entry(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            month = cursor.getString(cursor.getColumnIndexOrThrow("month")),
            date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
            sugar = cursor.getDouble(cursor.getColumnIndexOrThrow("sugar")),
            steps = cursor.getInt(cursor.getColumnIndexOrThrow("steps")),
            readingType = cursor.getString(cursor.getColumnIndexOrThrow("reading_type")),
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            year = cursor.getInt(cursor.getColumnIndexOrThrow("year"))
        )
    }

    companion object {
        const val DB_NAME = "health.db"
        private const val KEY_ALIAS = "hundredlife_health_db_passphrase"
        private const val KEY_FILE = "health_db_passphrase.bin"

        private fun openPassphrase(context: Context): String {
            System.loadLibrary("sqlcipher")
            discardLegacyPlaintextDatabase(context)
            return getOrCreatePassphrase(context)
        }

        private fun discardLegacyPlaintextDatabase(context: Context) {
            val file = context.getDatabasePath(DB_NAME)
            if (!file.isFile) return
            if (file.length() == 0L) {
                if (!context.deleteDatabase(DB_NAME)) {
                    throw IllegalStateException("Could not remove an empty health.db")
                }
                return
            }
            val header = ByteArray(SqliteFileFormat.HEADER.size)
            val read = file.inputStream().use { it.read(header) }
            if (read == header.size && SqliteFileFormat.isPlaintextSqlite(header)) {
                if (!context.deleteDatabase(DB_NAME)) {
                    throw IllegalStateException(
                        "Found an unencrypted health.db and could not remove it. " +
                            "Refusing to keep storing readings in plaintext."
                    )
                }
            }
        }

        private fun getOrCreatePassphrase(context: Context): String {
            val file = File(context.noBackupFilesDir, KEY_FILE)
            if (file.exists()) {
                if (!KeystoreAes.hasKey(KEY_ALIAS)) {
                    throw IllegalStateException(
                        "Health database key file exists but its Android Keystore key is missing. " +
                            "The encrypted database was not deleted."
                    )
                }
                val plain = KeystoreAes.decrypt(KEY_ALIAS, file.readBytes())
                return String(plain, Charsets.UTF_8)
            }
            val database = context.getDatabasePath(DB_NAME)
            if (database.isFile && database.length() > 0L) {
                throw IllegalStateException(
                    "An encrypted health database exists without its key file. " +
                        "Refusing to create a new key over it."
                )
            }
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val passphrase = Base64.encodeToString(raw, Base64.NO_WRAP)
            val encrypted = KeystoreAes.encrypt(KEY_ALIAS, passphrase.toByteArray(Charsets.UTF_8))
            val tmp = File(file.parentFile, "$KEY_FILE.tmp")
            tmp.writeBytes(encrypted)
            if (!tmp.renameTo(file)) {
                file.writeBytes(encrypted)
                tmp.delete()
            }
            return passphrase
        }
    }
}

data class Entry(
    val id: Int,
    val month: String,
    val date: String,
    val sugar: Double,
    val steps: Int,
    val readingType: String = "Fasting",
    val notes: String = "",
    val year: Int = 2026
)
