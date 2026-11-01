package de.asdfghasdfgh.ie.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where Home Assistant lives, the long-lived token that lets this app in, and, optionally, an
 * address to connect to instead of what DNS says for the name.
 */
data class Connection(
    val baseUrl: String,
    val token: String,
    val serverAddress: String = "",
) {
    val usable: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

/**
 * Kept in the app's private storage. The token is encrypted with a key that never leaves the
 * Android Keystore, so a copy of the preferences file is not a copy of the token. The token
 * still has the rights of the Home Assistant user that made it: make a user for this app and
 * give it only what it needs.
 */
object Prefs {
    private const val FILE = "ie"
    private const val BASE_URL = "base_url"
    private const val SERVER_ADDRESS = "server_address"
    private const val SELECTED = "selected_entities"
    private const val TOKEN_ENCRYPTED = "token_encrypted"

    /** Only read to bring a token written by an older build under encryption. */
    private const val TOKEN_LEGACY = "token"

    fun load(context: Context): Connection {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Connection(
            baseUrl = prefs.getString(BASE_URL, "").orEmpty(),
            token = tokenFrom(prefs),
            serverAddress = prefs.getString(SERVER_ADDRESS, "").orEmpty(),
        )
    }

    fun save(context: Context, connection: Connection) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(BASE_URL, connection.baseUrl)
            .putString(TOKEN_ENCRYPTED, TokenCipher.encrypt(connection.token))
            .putString(SERVER_ADDRESS, connection.serverAddress)
            .remove(TOKEN_LEGACY)
            .apply()
    }

    /** The entities chosen for the one screen, or null while every supported one is shown. */
    fun selection(context: Context): Set<String>? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!prefs.contains(SELECTED)) return null
        return prefs.getStringSet(SELECTED, emptySet()).orEmpty().toSet()
    }

    fun saveSelection(context: Context, entityIds: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(SELECTED, entityIds)
            .apply()
    }

    /**
     * Forgets the server and its token, but keeps the chosen entities: signing in again to the
     * same house should not put every device back on the screen.
     */
    fun clearConnection(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(BASE_URL)
            .remove(TOKEN_ENCRYPTED)
            .remove(TOKEN_LEGACY)
            .remove(SERVER_ADDRESS)
            .apply()
    }

    private fun tokenFrom(prefs: android.content.SharedPreferences): String {
        val encrypted = prefs.getString(TOKEN_ENCRYPTED, null)
        if (!encrypted.isNullOrEmpty()) return TokenCipher.decrypt(encrypted)

        val legacy = prefs.getString(TOKEN_LEGACY, null)
        if (legacy.isNullOrEmpty()) return ""
        runCatching {
            prefs.edit()
                .putString(TOKEN_ENCRYPTED, TokenCipher.encrypt(legacy))
                .remove(TOKEN_LEGACY)
                .apply()
        }
        return legacy
    }
}

/** AES-GCM with a key held by the Android Keystore, its IV stored beside the ciphertext. */
private object TokenCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "de.asdfghasdfgh.ie.token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrDefault("")

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
