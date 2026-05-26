package helium314.keyboard.latin.lanboard

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class WhisperClient {

    companion object {
        private const val TAG = "WhisperClient"
        private const val HEALTH_TIMEOUT_MS = 1500
        private const val TRANSCRIBE_TIMEOUT_MS = 30000
        private const val MODEL_TIMEOUT_MS = 5000
    }

    enum class AuthMode { NONE, BEARER, BASIC }

    data class Config(
        val serverUrl: String,
        val model: String = "",
        val language: String = "en",
        val authMode: AuthMode = AuthMode.NONE,
        val authToken: String = "",
        val authUser: String = "",
        val authPass: String = ""
    )

    data class TranscriptionResult(
        val text: String,
        val success: Boolean,
        val error: String? = null,
        val isAuthError: Boolean = false
    )

    private val hallucinations = setOf(
        "okay.", "all right.", "thank you.", "thanks for watching.",
        "bye.", "you", "thanks.", "thank you for watching.",
        "the end.", "so,", "i'm going to go ahead and do that.",
    )

    data class HealthResult(
        val healthy: Boolean,
        val detail: String? = null
    )

    suspend fun checkHealth(config: Config): Boolean = checkHealthDetailed(config).healthy

    suspend fun checkHealthDetailed(config: Config): HealthResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("${config.serverUrl.trimEnd('/')}/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = HEALTH_TIMEOUT_MS
            conn.readTimeout = HEALTH_TIMEOUT_MS
            applyAuth(conn, config)

            val code = conn.responseCode
            conn.disconnect()
            if (code == 200) {
                HealthResult(healthy = true)
            } else if (code == 401) {
                HealthResult(healthy = false, detail = "Authentication rejected (401)")
            } else {
                HealthResult(healthy = false, detail = "Server returned $code")
            }
        } catch (e: java.net.ConnectException) {
            Log.d(TAG, "Health check failed: ${e.message}")
            HealthResult(healthy = false, detail = "Connection refused — is the server running?")
        } catch (e: java.net.SocketTimeoutException) {
            Log.d(TAG, "Health check failed: ${e.message}")
            HealthResult(healthy = false, detail = "Timed out — check URL and network")
        } catch (e: java.net.UnknownHostException) {
            Log.d(TAG, "Health check failed: ${e.message}")
            HealthResult(healthy = false, detail = "Host not found — check the URL")
        } catch (e: Exception) {
            Log.d(TAG, "Health check failed: ${e.message}")
            HealthResult(healthy = false, detail = e.message)
        }
    }

    suspend fun fetchModels(config: Config): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${config.serverUrl.trimEnd('/')}/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = MODEL_TIMEOUT_MS
            conn.readTimeout = MODEL_TIMEOUT_MS
            applyAuth(conn, config)

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val models = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val model = data.getJSONObject(i)
                models.add(model.getString("id"))
            }
            models
        } catch (e: Exception) {
            Log.e(TAG, "Fetch models failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun transcribe(config: Config, wavData: ByteArray, preset: String? = null): TranscriptionResult =
        withContext(Dispatchers.IO) {
            try {
                val boundary = "----LANboard${UUID.randomUUID()}"
                val url = URL("${config.serverUrl.trimEnd('/')}/v1/audio/transcriptions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = TRANSCRIBE_TIMEOUT_MS
                conn.readTimeout = TRANSCRIBE_TIMEOUT_MS
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                applyAuth(conn, config)

                val body = buildMultipartBody(boundary, config, wavData, preset)
                conn.setRequestProperty("Content-Length", body.size.toString())
                conn.outputStream.use { it.write(body) }

                val code = conn.responseCode
                if (code == 401) {
                    conn.disconnect()
                    return@withContext TranscriptionResult(
                        text = "",
                        success = false,
                        error = "Authentication failed — check your token in settings",
                        isAuthError = true
                    )
                }

                if (code != 200) {
                    val errorBody = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                    conn.disconnect()
                    return@withContext TranscriptionResult(
                        text = "",
                        success = false,
                        error = "Server returned $code: $errorBody"
                    )
                }

                val responseBody = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(responseBody)
                val rawText = json.optString("text", "").trim()

                if (rawText.isEmpty() || hallucinations.contains(rawText.lowercase())) {
                    return@withContext TranscriptionResult(
                        text = "",
                        success = true,
                        error = "No speech detected"
                    )
                }

                TranscriptionResult(text = rawText, success = true)
            } catch (e: IOException) {
                Log.e(TAG, "Transcription failed: ${e.message}")
                TranscriptionResult(
                    text = "",
                    success = false,
                    error = "Connection failed: ${e.message}"
                )
            }
        }

    private fun buildMultipartBody(
        boundary: String,
        config: Config,
        wavData: ByteArray,
        preset: String?
    ): ByteArray {
        val output = ByteArrayOutputStream()

        fun writePart(name: String, value: String) {
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            output.write("$value\r\n".toByteArray())
        }

        fun writeFilePart(name: String, filename: String, contentType: String, data: ByteArray) {
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n".toByteArray())
            output.write("Content-Type: $contentType\r\n\r\n".toByteArray())
            output.write(data)
            output.write("\r\n".toByteArray())
        }

        writeFilePart("file", "audio.wav", "audio/wav", wavData)

        if (config.model.isNotBlank()) {
            writePart("model", config.model)
        }

        writePart("language", config.language)
        writePart("response_format", "json")
        writePart("condition_on_previous_text", "false")

        if (!preset.isNullOrBlank()) {
            writePart("prompt", preset)
            writePart("initial_prompt", preset)
        }

        output.write("--$boundary--\r\n".toByteArray())
        return output.toByteArray()
    }

    private fun applyAuth(conn: HttpURLConnection, config: Config) {
        when (config.authMode) {
            AuthMode.BEARER -> {
                if (config.authToken.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer ${config.authToken}")
                }
            }
            AuthMode.BASIC -> {
                if (config.authUser.isNotBlank()) {
                    val credentials = Base64.encodeToString(
                        "${config.authUser}:${config.authPass}".toByteArray(),
                        Base64.NO_WRAP
                    )
                    conn.setRequestProperty("Authorization", "Basic $credentials")
                }
            }
            AuthMode.NONE -> { /* no auth header */ }
        }
    }
}
