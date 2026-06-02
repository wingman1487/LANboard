package helium314.keyboard.latin.lanboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class VoicePresetManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "lanboard_presets"
        private const val KEY_PRESETS = "presets"
        private const val KEY_ACTIVE = "active_preset"
        private const val SOFT_LIMIT_WORDS = 150
    }

    data class Preset(
        val name: String,
        val promptText: String,
        val isDefault: Boolean = false,
        /** per-preset color as an ARGB hex string (§6.2/§8.1, Q2). Drives the mic-icon tint and the
         *  listening spike-tip color. Defaults to General cyan; curated from [LBPresetPalette]. */
        val colorHex: String = LBPresetPalette.GENERAL_CYAN_HEX,
        /** short subtitle shown under the name on the Manage Presets list (§9.3 render). Optional;
         *  blank for user presets, which simply show no subtitle. */
        val description: String = ""
    ) {
        fun wordCount(): Int = if (promptText.isBlank()) 0 else promptText.trim().split(Regex("\\s+")).size
        fun isOverLimit(): Boolean = wordCount() > SOFT_LIMIT_WORDS

        /** the resolved ARGB int for [colorHex], falling back to General cyan if it is malformed. */
        fun colorInt(): Int = LBPresetPalette.parseOrDefault(colorHex)

        /** True for the permanent, non-deletable, always-cyan General preset (§6.2/§8.1). */
        fun isGeneral(): Boolean = LBPresetPalette.isGeneral(name)
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_PRESETS)) {
            initDefaults()
        }
    }

    private fun initDefaults() {
        val defaults = listOf(
            Preset(
                "Coding",
                "Programming, software development, Python, Kotlin, TypeScript, JavaScript, " +
                    "React, Docker, Kubernetes, Git, GitHub, API, REST, JSON, SQL, PostgreSQL, " +
                    "refactor, deploy, merge, commit, pull request, code review",
                isDefault = true,
                colorHex = LBPresetPalette.CODING_HEX,
                description = "Termux · code editors"
            ),
            Preset(
                "Email",
                "Professional email correspondence. Formal tone, complete sentences, " +
                    "proper punctuation. Dear, Regards, Best wishes, Please find attached, " +
                    "I hope this email finds you well, Looking forward to hearing from you",
                isDefault = true,
                colorHex = LBPresetPalette.EMAIL_HEX,
                description = "Professional correspondence"
            ),
            Preset(
                "Personal",
                "Casual messaging, text messages, chat. Contractions okay, informal tone. " +
                    "Hey, what's up, gonna, wanna, yeah, nah, cool, awesome, lol",
                isDefault = true,
                colorHex = LBPresetPalette.PERSONAL_HEX,
                description = "Messages · social"
            ),
            Preset(
                "Terminal",
                "Shell commands, terminal, bash, zsh, ssh, git, docker, kubectl, systemctl, " +
                    "sudo, apt, pip, npm, yarn, cargo, grep, sed, awk, curl, wget, " +
                    "chmod, chown, ls, cd, mkdir, rm, cp, mv, cat, less, tail, head",
                isDefault = true,
                colorHex = LBPresetPalette.TERMINAL_HEX,
                description = "Shell · git · docker"
            ),
            Preset(
                "General",
                "",
                isDefault = true,
                colorHex = LBPresetPalette.GENERAL_HEX,
                description = "Default fallback · the signature"
            )
        )
        savePresets(defaults)
        setActivePreset("General")
    }

    fun getPresets(): List<Preset> {
        val json = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                Preset(
                    name = name,
                    promptText = obj.optString("promptText", ""),
                    isDefault = obj.optBoolean("isDefault", false),
                    // back-compat: presets stored before colors existed have no "colorHex" key —
                    // fall back to the locked default for that name (or General cyan for others)
                    colorHex = obj.optString("colorHex", LBPresetPalette.defaultHexForName(name)),
                    description = obj.optString("description", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePresets(presets: List<Preset>) {
        val array = JSONArray()
        for (preset in presets) {
            array.put(JSONObject().apply {
                put("name", preset.name)
                put("promptText", preset.promptText)
                put("isDefault", preset.isDefault)
                put("colorHex", preset.colorHex)
                put("description", preset.description)
            })
        }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    fun getActivePreset(): Preset? {
        val activeName = prefs.getString(KEY_ACTIVE, "General") ?: "General"
        return getPresets().find { it.name == activeName }
    }

    fun setActivePreset(name: String) {
        prefs.edit().putString(KEY_ACTIVE, name).apply()
    }

    fun addPreset(preset: Preset) {
        val presets = getPresets().toMutableList()
        presets.add(preset)
        savePresets(presets)
    }

    fun updatePreset(name: String, newPreset: Preset) {
        val presets = getPresets().toMutableList()
        val index = presets.indexOfFirst { it.name == name }
        if (index >= 0) {
            presets[index] = newPreset
            savePresets(presets)
            if (prefs.getString(KEY_ACTIVE, "") == name && name != newPreset.name) {
                setActivePreset(newPreset.name)
            }
        }
    }

    /**
     * Delete a preset. General is permanent and is never removed (§6.2/§8.1 — guarded here as well as
     * in the UI). On deletion the active preset cascades back to General (§8.1/G1).
     */
    fun deletePreset(name: String) {
        if (LBPresetPalette.isGeneral(name)) return
        val presets = getPresets().toMutableList()
        presets.removeAll { it.name == name }
        savePresets(presets)
        if (prefs.getString(KEY_ACTIVE, "") == name) {
            setActivePreset("General")
        }
    }

    /**
     * Move the preset at [fromIndex] to [toIndex], persisting the new global order. This single order
     * drives the quick-picker fan order and the per-app cold-start default (§9.3). Out-of-range indices
     * are a no-op so a stray drag can never corrupt storage.
     */
    fun reorderPresets(fromIndex: Int, toIndex: Int) {
        val presets = getPresets().toMutableList()
        if (fromIndex !in presets.indices || toIndex !in presets.indices || fromIndex == toIndex) return
        presets.add(toIndex, presets.removeAt(fromIndex))
        savePresets(presets)
    }
}
