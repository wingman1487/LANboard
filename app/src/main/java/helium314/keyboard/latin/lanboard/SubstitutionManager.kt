package helium314.keyboard.latin.lanboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class SubstitutionManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "lanboard_substitutions"
        private const val KEY_RULES = "rules"
    }

    data class Rule(val heard: String, val corrected: String)

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_RULES)) {
            initDefaults()
        }
    }

    private fun initDefaults() {
        val defaults = listOf(
            Rule("true nas", "TrueNAS"),
            Rule("truenas", "TrueNAS"),
            Rule("ollama", "Ollama"),
            Rule("oh llama", "Ollama"),
            Rule("pi qt", "PyQt"),
            Rule("pie qt", "PyQt"),
            Rule("kube cuddle", "kubectl"),
            Rule("cube control", "kubectl"),
            Rule("kube control", "kubectl"),
            Rule("docker", "Docker"),
            Rule("kubernetes", "Kubernetes"),
            Rule("fast api", "FastAPI"),
            Rule("github", "GitHub"),
            Rule("pi test", "pytest"),
        )
        saveRules(defaults)
    }

    fun getRules(): List<Rule> {
        val json = prefs.getString(KEY_RULES, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Rule(
                    heard = obj.getString("heard"),
                    corrected = obj.getString("corrected")
                )
            }.sortedByDescending { it.heard.length }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRules(rules: List<Rule>) {
        val array = JSONArray()
        for (rule in rules) {
            array.put(JSONObject().apply {
                put("heard", rule.heard)
                put("corrected", rule.corrected)
            })
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    fun addRule(rule: Rule) {
        val rules = getRules().toMutableList()
        rules.add(rule)
        saveRules(rules)
    }

    fun deleteRule(heard: String) {
        val rules = getRules().toMutableList()
        rules.removeAll { it.heard.equals(heard, ignoreCase = true) }
        saveRules(rules)
    }

    fun applySubstitutions(text: String): String {
        var result = text
        val rules = getRules() // already sorted longest-first
        for (rule in rules) {
            result = result.replace(rule.heard, rule.corrected, ignoreCase = true)
        }
        return result
    }
}
