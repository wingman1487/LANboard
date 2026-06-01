package helium314.keyboard.latin.lanboard

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LANboardConfig(context: Context) {

    companion object {
        private const val PREFS_NAME = "lanboard_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_MODEL = "model"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_TERMINAL_DEFAULT = "terminal_row_default"
        private const val KEY_COPY_TO_CLIPBOARD = "copy_to_clipboard"
        private const val KEY_SENSITIVE_FIELD_POLICY = "sensitive_field_policy"
        private const val KEY_WIZARD_COMPLETED = "wizard_completed"

        // Encrypted keys stored via Android Keystore
        private const val KEY_AUTH_TOKEN_ENC = "auth_token_enc"
        private const val KEY_AUTH_TOKEN_IV = "auth_token_iv"
        private const val KEY_AUTH_USER_ENC = "auth_user_enc"
        private const val KEY_AUTH_USER_IV = "auth_user_iv"
        private const val KEY_AUTH_PASS_ENC = "auth_pass_enc"
        private const val KEY_AUTH_PASS_IV = "auth_pass_iv"

        private const val KEYSTORE_ALIAS = "lanboard_auth_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    enum class SensitiveFieldPolicy { WARN_FIRST, ALWAYS_ALLOW, ALWAYS_BLOCK }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var authMode: WhisperClient.AuthMode
        get() = try {
            WhisperClient.AuthMode.valueOf(
                prefs.getString(KEY_AUTH_MODE, "NONE") ?: "NONE"
            )
        } catch (_: Exception) { WhisperClient.AuthMode.NONE }
        set(value) = prefs.edit().putString(KEY_AUTH_MODE, value.name).apply()

    var terminalRowDefault: Boolean
        get() = prefs.getBoolean(KEY_TERMINAL_DEFAULT, false)
        set(value) = prefs.edit().putBoolean(KEY_TERMINAL_DEFAULT, value).apply()

    // v2 §5.5 clipboard fallback: live commits are also copied to the clipboard. Default ON.
    var copyTranscriptionsToClipboard: Boolean
        get() = prefs.getBoolean(KEY_COPY_TO_CLIPBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_COPY_TO_CLIPBOARD, value).apply()

    var sensitiveFieldPolicy: SensitiveFieldPolicy
        get() = try {
            SensitiveFieldPolicy.valueOf(
                prefs.getString(KEY_SENSITIVE_FIELD_POLICY, "WARN_FIRST") ?: "WARN_FIRST"
            )
        } catch (_: Exception) { SensitiveFieldPolicy.WARN_FIRST }
        set(value) = prefs.edit().putString(KEY_SENSITIVE_FIELD_POLICY, value.name).apply()

    var wizardCompleted: Boolean
        get() = prefs.getBoolean(KEY_WIZARD_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_WIZARD_COMPLETED, value).apply()

    // Encrypted credential storage via Android Keystore
    var authToken: String
        get() = decrypt(KEY_AUTH_TOKEN_ENC, KEY_AUTH_TOKEN_IV)
        set(value) = encrypt(value, KEY_AUTH_TOKEN_ENC, KEY_AUTH_TOKEN_IV)

    var authUser: String
        get() = decrypt(KEY_AUTH_USER_ENC, KEY_AUTH_USER_IV)
        set(value) = encrypt(value, KEY_AUTH_USER_ENC, KEY_AUTH_USER_IV)

    var authPass: String
        get() = decrypt(KEY_AUTH_PASS_ENC, KEY_AUTH_PASS_IV)
        set(value) = encrypt(value, KEY_AUTH_PASS_ENC, KEY_AUTH_PASS_IV)

    fun toWhisperConfig(): WhisperClient.Config = WhisperClient.Config(
        serverUrl = serverUrl,
        model = model,
        language = language,
        authMode = authMode,
        authToken = authToken,
        authUser = authUser,
        authPass = authPass
    )

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun encrypt(value: String, encKey: String, ivKey: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(encKey).remove(ivKey).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(encKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            // Fallback: store empty on encryption failure
            prefs.edit().remove(encKey).remove(ivKey).apply()
        }
    }

    private fun decrypt(encKey: String, ivKey: String): String {
        val encData = prefs.getString(encKey, null) ?: return ""
        val ivData = prefs.getString(ivKey, null) ?: return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivData, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(Base64.decode(encData, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
