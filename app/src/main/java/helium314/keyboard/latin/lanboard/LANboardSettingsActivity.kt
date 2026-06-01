package helium314.keyboard.latin.lanboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import helium314.keyboard.latin.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Design tokens ──────────────────────────────────────────────────────────────

internal object LBColors {
    val Background = Color(0xFF0A0D10)
    val Surface = Color(0xFF161B22)
    val ElevatedSurface = Color(0xFF1C232C)
    val Border = Color(0xFF2A323C)
    val BorderBright = Color(0xFF3A4451)
    val Primary = Color(0xFF00D4FF)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF9BA8B4)
    val TextTertiary = Color(0xFF6B7785)
    val TextQuiet = Color(0xFF4A5360)
    val GreenPulse = Color(0xFF3DDC97)
    val Amber = Color(0xFFF0B429)
    val Red = Color(0xFFEF4444)
}

internal val lanboardDarkScheme = darkColorScheme(
    background = LBColors.Background,
    surface = LBColors.Surface,
    surfaceVariant = LBColors.ElevatedSurface,
    primary = LBColors.Primary,
    onBackground = LBColors.TextPrimary,
    onSurface = LBColors.TextPrimary,
    onSurfaceVariant = LBColors.TextSecondary,
)

// ─── Navigation routes ──────────────────────────────────────────────────────────

private object Routes {
    const val MAIN = "settings_main"
    const val PRESET_LIST = "preset_list"
    const val PRESET_EDIT = "preset_edit/{name}"
    const val PRESET_NEW = "preset_new"
    const val SUBSTITUTIONS = "substitutions"
    const val PENDING_QUEUE = "pending_queue"
    const val ABOUT = "about"

    fun presetEdit(name: String) = "preset_edit/$name"
}

// ─── Activity ───────────────────────────────────────────────────────────────────

class LANboardSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = lanboardDarkScheme) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Routes.MAIN,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LBColors.Background)
                        .systemBarsPadding()
                ) {
                    composable(Routes.MAIN) { MainSettingsScreen(navController) }
                    composable(Routes.PRESET_LIST) { PresetListScreen(navController) }
                    composable(Routes.PRESET_EDIT) { backStackEntry ->
                        val name = backStackEntry.arguments?.getString("name") ?: ""
                        PresetEditScreen(navController, presetName = name)
                    }
                    composable(Routes.PRESET_NEW) {
                        PresetEditScreen(navController, presetName = null)
                    }
                    composable(Routes.SUBSTITUTIONS) { SubstitutionsScreen(navController) }
                    composable(Routes.PENDING_QUEUE) { PendingQueueScreen(navController) }
                    composable(Routes.ABOUT) { AboutScreen(navController) }
                }
            }
        }
    }
}

// ─── Main Settings Screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val config = remember { LANboardConfig(context) }
    val whisperClient = remember { WhisperClient() }
    val presetManager = remember { VoicePresetManager(context) }
    val pendingManager = remember { PendingRecordingsManager(context) }
    val scope = rememberCoroutineScope()

    // ── Server state ──
    var serverUrl by remember { mutableStateOf(config.serverUrl) }
    var model by remember { mutableStateOf(config.model) }
    var language by remember { mutableStateOf(config.language) }
    var authMode by remember { mutableStateOf(config.authMode) }
    var authToken by remember { mutableStateOf(config.authToken) }
    var authUser by remember { mutableStateOf(config.authUser) }
    var authPass by remember { mutableStateOf(config.authPass) }
    var showToken by remember { mutableStateOf(false) }
    var showPass by remember { mutableStateOf(false) }

    // Connection health
    var connectionHealthy by remember { mutableStateOf<Boolean?>(null) }
    var healthChecking by remember { mutableStateOf(false) }

    // Model dropdown
    val availableModels = remember { mutableStateListOf<String>() }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var modelsLoading by remember { mutableStateOf(false) }

    // Auth dropdown
    var authDropdownExpanded by remember { mutableStateOf(false) }

    // Test connection
    var testResult by remember { mutableStateOf<String?>(null) }
    var testRunning by remember { mutableStateOf(false) }

    // ── Voice accuracy state ──
    var activePresetName by remember { mutableStateOf(presetManager.getActivePreset()?.name ?: "General") }
    var pendingCount by remember { mutableStateOf(pendingManager.getPendingCount()) }
    var copyToClipboard by remember { mutableStateOf(config.copyTranscriptionsToClipboard) }
    var pauseMedia by remember { mutableStateOf(config.pauseMediaDuringRecording) }

    // ── Keyboard state ──
    var terminalRowDefault by remember { mutableStateOf(config.terminalRowDefault) }
    var sensitiveFieldPolicy by remember { mutableStateOf(config.sensitiveFieldPolicy) }
    var sensitiveDropdownExpanded by remember { mutableStateOf(false) }

    // Live health check on URL change
    LaunchedEffect(serverUrl) {
        if (serverUrl.isBlank()) {
            connectionHealthy = null
            return@LaunchedEffect
        }
        delay(500) // debounce
        healthChecking = true
        val cfg = config.toWhisperConfig().copy(serverUrl = serverUrl)
        connectionHealthy = whisperClient.checkHealth(cfg)
        healthChecking = false
    }

    // Refresh active preset when returning from drill-down
    LaunchedEffect(navController.currentBackStackEntry) {
        activePresetName = presetManager.getActivePreset()?.name ?: "General"
        pendingCount = pendingManager.getPendingCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "LANboard Settings",
            color = LBColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ═══════════════════════ SERVER ═══════════════════════
        SettingsSection("Server") {
            // Server URL
            LBTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    config.serverUrl = it
                },
                label = "Server URL",
                placeholder = "http://192.168.1.100:8080"
            )

            Spacer(Modifier.height(8.dp))

            // Connection status
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (dotColor, statusText) = when {
                    healthChecking -> LBColors.Amber to "Checking..."
                    connectionHealthy == true -> LBColors.GreenPulse to "Connected"
                    connectionHealthy == false -> LBColors.Red to "Unreachable"
                    else -> LBColors.TextQuiet to "Not configured"
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(statusText, color = LBColors.TextSecondary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Model dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    LBTextField(
                        value = model.ifBlank { "(none)" },
                        onValueChange = {},
                        label = "Model",
                        readOnly = true,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                        containerColor = LBColors.ElevatedSurface
                    ) {
                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, color = LBColors.TextPrimary) },
                                onClick = {
                                    model = m
                                    config.model = m
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                        if (availableModels.isEmpty() && !modelsLoading) {
                            DropdownMenuItem(
                                text = { Text("No models found", color = LBColors.TextTertiary) },
                                onClick = { modelDropdownExpanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        scope.launch {
                            modelsLoading = true
                            val cfg = config.toWhisperConfig().copy(serverUrl = serverUrl)
                            val models = whisperClient.fetchModels(cfg)
                            availableModels.clear()
                            availableModels.addAll(models)
                            modelsLoading = false
                        }
                    }
                ) {
                    if (modelsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = LBColors.Primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("↻", color = LBColors.Primary, fontSize = 20.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Language
            LBTextField(
                value = language,
                onValueChange = {
                    language = it
                    config.language = it
                },
                label = "Language",
                placeholder = "en"
            )

            Spacer(Modifier.height(12.dp))

            // Authentication
            ExposedDropdownMenuBox(
                expanded = authDropdownExpanded,
                onExpandedChange = { authDropdownExpanded = it }
            ) {
                LBTextField(
                    value = when (authMode) {
                        WhisperClient.AuthMode.NONE -> "None"
                        WhisperClient.AuthMode.BEARER -> "Bearer"
                        WhisperClient.AuthMode.BASIC -> "Basic"
                    },
                    onValueChange = {},
                    label = "Authentication",
                    readOnly = true,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authDropdownExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = authDropdownExpanded,
                    onDismissRequest = { authDropdownExpanded = false },
                    containerColor = LBColors.ElevatedSurface
                ) {
                    WhisperClient.AuthMode.entries.forEach { mode ->
                        val label = when (mode) {
                            WhisperClient.AuthMode.NONE -> "None"
                            WhisperClient.AuthMode.BEARER -> "Bearer"
                            WhisperClient.AuthMode.BASIC -> "Basic"
                        }
                        DropdownMenuItem(
                            text = { Text(label, color = LBColors.TextPrimary) },
                            onClick = {
                                authMode = mode
                                config.authMode = mode
                                authDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Conditional auth fields
            AnimatedVisibility(visible = authMode == WhisperClient.AuthMode.BEARER) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    LBTextField(
                        value = authToken,
                        onValueChange = {
                            authToken = it
                            config.authToken = it
                        },
                        label = "Bearer Token",
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                if (showToken) "Hide" else "Show",
                                color = LBColors.Primary,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { showToken = !showToken }
                            )
                        }
                    )
                }
            }

            AnimatedVisibility(visible = authMode == WhisperClient.AuthMode.BASIC) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    LBTextField(
                        value = authUser,
                        onValueChange = {
                            authUser = it
                            config.authUser = it
                        },
                        label = "Username"
                    )
                    Spacer(Modifier.height(8.dp))
                    LBTextField(
                        value = authPass,
                        onValueChange = {
                            authPass = it
                            config.authPass = it
                        },
                        label = "Password",
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                if (showPass) "Hide" else "Show",
                                color = LBColors.Primary,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { showPass = !showPass }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Test connection button
            Button(
                onClick = {
                    scope.launch {
                        testRunning = true
                        testResult = null
                        val startMs = System.currentTimeMillis()
                        val cfg = config.toWhisperConfig().copy(
                            serverUrl = serverUrl, model = model, language = language,
                            authMode = authMode, authToken = authToken,
                            authUser = authUser, authPass = authPass
                        )
                        val result = whisperClient.checkHealthDetailed(cfg)
                        val elapsed = System.currentTimeMillis() - startMs
                        testResult = if (result.healthy) {
                            "Connected · ${elapsed}ms"
                        } else {
                            result.detail ?: "Connection failed"
                        }
                        connectionHealthy = result.healthy
                        testRunning = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LBColors.ElevatedSurface,
                    contentColor = LBColors.Primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LBColors.Primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Test connection")
            }

            // Test result pill
            testResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                val isSuccess = result.startsWith("Connected")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSuccess) LBColors.GreenPulse.copy(alpha = 0.15f)
                            else LBColors.Red.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        result,
                        color = if (isSuccess) LBColors.GreenPulse else LBColors.Red,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ═══════════════════════ VOICE ACCURACY ═══════════════════════
        SettingsSection("Voice Accuracy") {
            // Voice presets
            SettingsRow(
                label = "Voice presets",
                value = activePresetName,
                onClick = { navController.navigate(Routes.PRESET_LIST) },
                showChevron = true
            )

            Spacer(Modifier.height(4.dp))

            // Substitutions
            SettingsRow(
                label = "Substitutions",
                value = "",
                onClick = { navController.navigate(Routes.SUBSTITUTIONS) },
                showChevron = true
            )

            Spacer(Modifier.height(8.dp))

            // Copy transcriptions to clipboard (v2 §5.5)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LBColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Copy transcriptions to clipboard", color = LBColors.TextPrimary, fontSize = 14.sp)
                Switch(
                    checked = copyToClipboard,
                    onCheckedChange = {
                        copyToClipboard = it
                        config.copyTranscriptionsToClipboard = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LBColors.Primary,
                        checkedTrackColor = LBColors.Primary.copy(alpha = 0.3f),
                        uncheckedThumbColor = LBColors.TextTertiary,
                        uncheckedTrackColor = LBColors.Border
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Pause media during recording (v2 §5.4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LBColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pause media during recording", color = LBColors.TextPrimary, fontSize = 14.sp)
                Switch(
                    checked = pauseMedia,
                    onCheckedChange = {
                        pauseMedia = it
                        config.pauseMediaDuringRecording = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LBColors.Primary,
                        checkedTrackColor = LBColors.Primary.copy(alpha = 0.3f),
                        uncheckedThumbColor = LBColors.TextTertiary,
                        uncheckedTrackColor = LBColors.Border
                    )
                )
            }
        }

        // ═══════════════════════ KEYBOARD ═══════════════════════
        SettingsSection("Keyboard") {
            // Terminal row default
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LBColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Terminal row default", color = LBColors.TextPrimary, fontSize = 14.sp)
                Switch(
                    checked = terminalRowDefault,
                    onCheckedChange = {
                        terminalRowDefault = it
                        config.terminalRowDefault = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LBColors.Primary,
                        checkedTrackColor = LBColors.Primary.copy(alpha = 0.3f),
                        uncheckedThumbColor = LBColors.TextTertiary,
                        uncheckedTrackColor = LBColors.Border
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Voice in sensitive fields
            ExposedDropdownMenuBox(
                expanded = sensitiveDropdownExpanded,
                onExpandedChange = { sensitiveDropdownExpanded = it }
            ) {
                LBTextField(
                    value = when (sensitiveFieldPolicy) {
                        LANboardConfig.SensitiveFieldPolicy.WARN_FIRST -> "Warn first"
                        LANboardConfig.SensitiveFieldPolicy.ALWAYS_ALLOW -> "Always allow"
                        LANboardConfig.SensitiveFieldPolicy.ALWAYS_BLOCK -> "Always block"
                    },
                    onValueChange = {},
                    label = "Voice in sensitive fields",
                    readOnly = true,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sensitiveDropdownExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = sensitiveDropdownExpanded,
                    onDismissRequest = { sensitiveDropdownExpanded = false },
                    containerColor = LBColors.ElevatedSurface
                ) {
                    LANboardConfig.SensitiveFieldPolicy.entries.forEach { policy ->
                        val label = when (policy) {
                            LANboardConfig.SensitiveFieldPolicy.WARN_FIRST -> "Warn first"
                            LANboardConfig.SensitiveFieldPolicy.ALWAYS_ALLOW -> "Always allow"
                            LANboardConfig.SensitiveFieldPolicy.ALWAYS_BLOCK -> "Always block"
                        }
                        DropdownMenuItem(
                            text = { Text(label, color = LBColors.TextPrimary) },
                            onClick = {
                                sensitiveFieldPolicy = policy
                                config.sensitiveFieldPolicy = policy
                                sensitiveDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // HeliBoard settings
            SettingsRow(
                label = "HeliBoard settings",
                value = "",
                onClick = {
                    context.startActivity(
                        Intent(context, helium314.keyboard.settings.SettingsActivity::class.java)
                    )
                },
                showChevron = true
            )
        }

        // ═══════════════════════ STORAGE ═══════════════════════
        SettingsSection("Storage") {
            SettingsRow(
                label = "Pending recordings",
                value = if (pendingCount > 0) "$pendingCount queued" else "None",
                onClick = { navController.navigate(Routes.PENDING_QUEUE) },
                showChevron = true
            )
        }

        // ═══════════════════════ ABOUT ═══════════════════════
        SettingsSection("About") {
            SettingsRow(
                label = "Credits & licenses",
                value = "",
                onClick = { navController.navigate(Routes.ABOUT) },
                showChevron = true
            )

            Spacer(Modifier.height(4.dp))

            SettingsRow(
                label = "Run setup again",
                value = "",
                onClick = {
                    context.startActivity(
                        Intent(context, LANboardWizardActivity::class.java)
                    )
                },
                showChevron = true
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LBColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Version", color = LBColors.TextSecondary, fontSize = 14.sp)
                Text(BuildConfig.VERSION_NAME, color = LBColors.TextTertiary, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Shared components ──────────────────────────────────────────────────────────

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title.uppercase(),
            color = LBColors.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
internal fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    showChevron: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LBColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = LBColors.TextPrimary, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotBlank()) {
                Text(value, color = LBColors.TextTertiary, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
            }
            if (showChevron || value.isNotBlank()) {
                Text("›", color = LBColors.TextQuiet, fontSize = 18.sp)
            }
        }
    }
}

@Composable
internal fun LBTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = LBColors.TextTertiary, fontSize = 12.sp) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, color = LBColors.TextQuiet, fontSize = 14.sp) }
        } else null,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LBColors.TextPrimary,
            unfocusedTextColor = LBColors.TextPrimary,
            focusedContainerColor = LBColors.Surface,
            unfocusedContainerColor = LBColors.Surface,
            focusedBorderColor = LBColors.Primary,
            unfocusedBorderColor = LBColors.Border,
            cursorColor = LBColors.Primary
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun LBTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "←",
            color = LBColors.TextSecondary,
            fontSize = 20.sp,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 12.dp)
        )
        Text(
            text = title,
            color = LBColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
