package my.toolmentum.hundredlife

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "HundredLifeSecure"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_LENGTH = 12
private const val FORMAT_VERSION: Byte = 1

private const val PREFS_FILE = "100life_settings_keystore"
private const val PREFS_KEY_ALIAS = "hundredlife_settings_key"
private const val LEGACY_PLAIN_PREFS = "100life_settings"
private const val LEGACY_CRYPTO_PREFS = "100life_settings_secure"

/**
 * Settings encrypted with an Android Keystore AES-GCM key.
 *
 * There is no plaintext fallback. If the keystore is unavailable, this throws
 * and callers must stop rather than writing health or identity fields to
 * ordinary SharedPreferences.
 *
 * Preference *names* (for example "patient_ic") are stored in the clear.
 * Values are not. The names are not secret; the IC and readings are.
 */
fun getSecurePrefs(context: Context): SharedPreferences {
    return KeystorePreferences.open(context.applicationContext)
}

internal object KeystoreAes {
    private val lock = Any()

    fun hasKey(alias: String): Boolean = synchronized(lock) {
        keyStore().containsAlias(alias)
    }

    fun ensureKey(alias: String) {
        synchronized(lock) {
            val ks = keyStore()
            if (!ks.containsAlias(alias)) {
                generate(alias)
            }
        }
    }

    fun encrypt(alias: String, plaintext: ByteArray): ByteArray = synchronized(lock) {
        val ks = keyStore()
        if (!ks.containsAlias(alias)) generate(alias)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(ks, alias))
        val iv = cipher.iv
        check(iv.size == GCM_IV_LENGTH) { "Unexpected GCM IV length ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext)
        ByteArray(1 + iv.size + ciphertext.size).also { out ->
            out[0] = FORMAT_VERSION
            iv.copyInto(out, destinationOffset = 1)
            ciphertext.copyInto(out, destinationOffset = 1 + iv.size)
        }
    }

    fun decrypt(alias: String, blob: ByteArray): ByteArray = synchronized(lock) {
        if (blob.size < 1 + GCM_IV_LENGTH + (GCM_TAG_BITS / 8) || blob[0] != FORMAT_VERSION) {
            throw IllegalArgumentException("Unrecognized ciphertext")
        }
        val ks = keyStore()
        val key = ks.getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("Android Keystore key is missing")
        val iv = blob.copyOfRange(1, 1 + GCM_IV_LENGTH)
        val ciphertext = blob.copyOfRange(1 + GCM_IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    }

    private fun secretKey(ks: KeyStore, alias: String): SecretKey {
        return ks.getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("Android Keystore key is missing")
    }

    private fun generate(alias: String) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    private fun keyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }
}

private class KeystorePreferences private constructor(
    private val store: SharedPreferences,
    private val alias: String
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> {
        val out = linkedMapOf<String, String>()
        for (key in store.all.keys) {
            getString(key, null)?.let { out[key] = it }
        }
        return out
    }

    override fun getString(key: String, defValue: String?): String? = decrypt(key) ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        throw UnsupportedOperationException("String sets are not used")
    }

    override fun getInt(key: String, defValue: Int): Int = decrypt(key)?.toIntOrNull() ?: defValue

    override fun getLong(key: String, defValue: Long): Long = decrypt(key)?.toLongOrNull() ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = decrypt(key)?.toFloatOrNull() ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return when (decrypt(key)) {
            "true" -> true
            "false" -> false
            else -> defValue
        }
    }

    override fun contains(key: String): Boolean = store.contains(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        store.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        store.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun decrypt(key: String): String? {
        val encoded = store.getString(key, null) ?: return null
        return try {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            String(KeystoreAes.decrypt(alias, blob), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt setting '$key'; leaving it unread")
            null
        }
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, String?>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            updates[key] = value?.let { encrypt(it) }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            throw UnsupportedOperationException("String sets are not used")
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = putString(key, value.toString())

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = putString(key, value.toString())

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = putString(key, value.toString())

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            putString(key, if (value) "true" else "false")

        override fun remove(key: String): SharedPreferences.Editor {
            updates[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            updates.clear()
            return this
        }

        override fun commit(): Boolean = write(commit = true)

        override fun apply() {
            write(commit = false)
        }

        private fun encrypt(value: String): String {
            val blob = KeystoreAes.encrypt(alias, value.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(blob, Base64.NO_WRAP)
        }

        private fun write(commit: Boolean): Boolean {
            val editor = store.edit()
            if (clearAll) editor.clear()
            for ((key, value) in updates) {
                if (value == null) editor.remove(key) else editor.putString(key, value)
            }
            return if (commit) editor.commit() else {
                editor.apply()
                true
            }
        }
    }

    companion object {
        fun open(context: Context): SharedPreferences {
            // Dev builds used to fall back to this plaintext file. Drop it so a
            // failed keystore init cannot leave identity fields behind in the clear.
            context.deleteSharedPreferences(LEGACY_PLAIN_PREFS)
            context.deleteSharedPreferences(LEGACY_CRYPTO_PREFS)

            val store = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            if (!KeystoreAes.hasKey(PREFS_KEY_ALIAS) && store.all.isNotEmpty()) {
                throw IllegalStateException(
                    "Encrypted settings exist but the Android Keystore key is missing. " +
                        "Refusing to replace them with plaintext."
                )
            }
            KeystoreAes.ensureKey(PREFS_KEY_ALIAS)
            return KeystorePreferences(store, PREFS_KEY_ALIAS)
        }
    }
}
