package helium314.keyboard.latin.lanboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

// Design tokens
private val Background = Color(0xFF0A0D10)
private val Surface = Color(0xFF161B22)
private val ElevatedSurface = Color(0xFF1C232C)
private val Border = Color(0xFF2A323C)
private val BorderBright = Color(0xFF3A4451)
private val Primary = Color(0xFF00D4FF)
private val CyanSoft = Color(0xFF4DD9EE)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF9BA8B4)
private val TextTertiary = Color(0xFF6B7785)
private val TextQuiet = Color(0xFF4A5360)
private val GreenPulse = Color(0xFF3DDC97)
private val Amber = Color(0xFFF0B429)
private val Red = Color(0xFFEF4444)

private const val SETUP_ARTICLE_URL = "https://github.com/LANboard-keyboard/docs/wiki/Setting-up-a-Whisper-server"
private const val TOTAL_STEPS = 5

class LANboardWizardActivity : ComponentActivity() {

    private var onResumeCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = wizardDarkScheme) {
                WizardScreen(
                    onFinish = {
                        val config = LANboardConfig(this)
                        config.wizardCompleted = true
                        finish()
                    },
                    onSkip = {
                        val config = LANboardConfig(this)
                        config.wizardCompleted = true
                        startActivity(Intent(this, LANboardSettingsActivity::class.java))
                        finish()
                    },
                    onRegisterResumeCallback = { callback ->
                        onResumeCallback = callback
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onResumeCallback?.invoke()
    }
}

private val wizardDarkScheme = darkColorScheme(
    background = Background,
    surface = Surface,
    primary = Primary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = Background,
)

// ---------------------------------------------------------------------------
// Root composable
// ---------------------------------------------------------------------------

@Composable
private fun WizardScreen(
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onRegisterResumeCallback: ((() -> Unit)?) -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Top bar with skip link and progress dots
        TopBar(
            currentStep = currentStep,
            onSkip = onSkip,
        )

        // Step content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it } + fadeOut())
            },
            label = "wizard_step",
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { step ->
            when (step) {
                0 -> StepWelcome(
                    onNext = { currentStep = 1 },
                    onSkip = onSkip,
                )
                1 -> StepMicPermission(
                    onNext = { currentStep = 2 },
                )
                2 -> StepServerSetup(
                    onNext = { currentStep = 3 },
                )
                3 -> StepEnableIME(
                    onNext = { currentStep = 4 },
                    onRegisterResumeCallback = onRegisterResumeCallback,
                )
                4 -> StepTryItOut(
                    onDone = onFinish,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar with progress dots and skip
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(currentStep: Int, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Skip link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "I know what I’m doing",
                color = TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onSkip() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0 until TOTAL_STEPS) {
                val color = when {
                    i == currentStep -> Primary
                    i < currentStep -> CyanSoft.copy(alpha = 0.4f)
                    else -> TextQuiet.copy(alpha = 0.5f)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                if (i < TOTAL_STEPS - 1) {
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1: Welcome
// ---------------------------------------------------------------------------

@Composable
private fun StepWelcome(onNext: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    StepContainer {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Welcome to LANboard",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "A voice keyboard for your own Whisper server. Audio goes to your hardware, " +
                    "not someone else’s cloud.\n\nSetup takes about three minutes — " +
                    "you’ll need a Whisper server already running on your network.",
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        PrimaryButton(text = "Get started", onClick = onNext)

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SETUP_ARTICLE_URL)))
        }) {
            Text(
                text = "I don’t have a server yet →",
                color = TextTertiary,
                fontSize = 14.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2: Mic permission
// ---------------------------------------------------------------------------

@Composable
private fun StepMicPermission(onNext: () -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        // Advance regardless of result
        onNext()
    }

    StepContainer {
        Spacer(modifier = Modifier.height(32.dp))

        StepTitle("Microphone access")
        StepBody(
            "LANboard needs the microphone to capture audio for transcription. " +
                    "We can’t request this from inside the keyboard itself — " +
                    "Android only allows it from here."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Permission card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .background(ElevatedSurface, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "RECORD_AUDIO",
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Captured audio is sent only to the server URL you provide in the next step.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        PrimaryButton(text = "Grant permission") {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }

        Spacer(modifier = Modifier.height(12.dp))

        SecondaryButton(text = "Skip — typing only") {
            onNext()
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3: Server setup
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepServerSetup(onNext: () -> Unit) {
    val context = LocalContext.current
    val config = remember { LANboardConfig(context) }
    val whisperClient = remember { WhisperClient() }
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(config.serverUrl) }
    var language by remember { mutableStateOf(config.language.ifBlank { "en" }) }

    // Auth state
    var authExpanded by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf(config.authMode) }
    var authToken by remember { mutableStateOf(config.authToken) }
    var authUser by remember { mutableStateOf(config.authUser) }
    var authPass by remember { mutableStateOf(config.authPass) }
    var authDropdownExpanded by remember { mutableStateOf(false) }

    // Test state
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestConnectionResult?>(null) }

    StepContainer {
        Spacer(modifier = Modifier.height(24.dp))

        StepTitle("Point at your server")
        StepBody("Enter the URL of your Whisper-compatible server. We’ll test the connection before continuing.")

        Spacer(modifier = Modifier.height(24.dp))

        // Server URL field
        WizardTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = "Server URL",
            placeholder = "http://192.168.1.100:8080",
            keyboardType = KeyboardType.Uri,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Language field
        WizardTextField(
            value = language,
            onValueChange = { language = it },
            label = "Language",
            placeholder = "en",
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Authentication disclosure
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .background(ElevatedSurface, RoundedCornerShape(12.dp))
                .clickable { authExpanded = !authExpanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Authentication",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (authMode == WhisperClient.AuthMode.NONE) "None" else authMode.name,
                        color = TextTertiary,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = if (authExpanded) "▲" else "▼",
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }

        if (authExpanded) {
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElevatedSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Most users: None",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Auth mode dropdown
                    ExposedDropdownMenuBox(
                        expanded = authDropdownExpanded,
                        onExpandedChange = { authDropdownExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = when (authMode) {
                                WhisperClient.AuthMode.NONE -> "None"
                                WhisperClient.AuthMode.BEARER -> "Bearer"
                                WhisperClient.AuthMode.BASIC -> "Basic"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Auth mode") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authDropdownExpanded) },
                            colors = wizardTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = authDropdownExpanded,
                            onDismissRequest = { authDropdownExpanded = false },
                            containerColor = ElevatedSurface,
                        ) {
                            WhisperClient.AuthMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (mode) {
                                                WhisperClient.AuthMode.NONE -> "None"
                                                WhisperClient.AuthMode.BEARER -> "Bearer"
                                                WhisperClient.AuthMode.BASIC -> "Basic"
                                            },
                                            color = TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        authMode = mode
                                        authDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    when (authMode) {
                        WhisperClient.AuthMode.BEARER -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            WizardTextField(
                                value = authToken,
                                onValueChange = { authToken = it },
                                label = "Token",
                                placeholder = "",
                                isMasked = true,
                            )
                        }
                        WhisperClient.AuthMode.BASIC -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            WizardTextField(
                                value = authUser,
                                onValueChange = { authUser = it },
                                label = "Username",
                                placeholder = "",
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            WizardTextField(
                                value = authPass,
                                onValueChange = { authPass = it },
                                label = "Password",
                                placeholder = "",
                                isMasked = true,
                            )
                        }
                        WhisperClient.AuthMode.NONE -> { /* nothing */ }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Test result pill
        testResult?.let { result ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (result.success) GreenPulse.copy(alpha = 0.12f)
                        else Red.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (result.success) GreenPulse.copy(alpha = 0.3f)
                        else Red.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(14.dp)
            ) {
                Text(
                    text = result.message,
                    color = if (result.success) GreenPulse else Red,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Test connection button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    // Save current values
                    config.serverUrl = serverUrl
                    config.language = language
                    config.authMode = authMode
                    config.authToken = authToken
                    config.authUser = authUser
                    config.authPass = authPass

                    isTesting = true
                    testResult = null
                    scope.launch {
                        val whisperConfig = config.toWhisperConfig()
                        val startTime = System.currentTimeMillis()
                        val healthy = whisperClient.checkHealth(whisperConfig)
                        val latency = System.currentTimeMillis() - startTime

                        if (healthy) {
                            val models = whisperClient.fetchModels(whisperConfig)
                            val modelName = models.firstOrNull() ?: "unknown"
                            val modelList = if (models.isNotEmpty()) models.joinToString(", ") else "none"
                            testResult = TestConnectionResult(
                                success = true,
                                message = "Connected · $modelName · ${latency}ms\n" +
                                        "Found ${models.size} model${if (models.size != 1) "s" else ""} loaded: $modelList"
                            )
                            if (models.isNotEmpty() && config.model.isBlank()) {
                                config.model = models.first()
                            }
                        } else {
                            testResult = TestConnectionResult(
                                success = false,
                                message = "Connection failed — check the URL and make sure your server is running."
                            )
                        }
                        isTesting = false
                    }
                },
                enabled = !isTesting && serverUrl.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Primary,
                    disabledContentColor = TextQuiet,
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = !isTesting && serverUrl.isNotBlank()).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(if (!isTesting && serverUrl.isNotBlank()) BorderBright else Border)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (isTesting) "Testing…" else if (testResult != null) "Test again" else "Test connection",
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Continue button
        PrimaryButton(
            text = "Continue",
            onClick = {
                // Save values before advancing
                config.serverUrl = serverUrl
                config.language = language
                config.authMode = authMode
                config.authToken = authToken
                config.authUser = authUser
                config.authPass = authPass
                onNext()
            }
        )

        // Warning if test failed or never run
        if (testResult != null && !testResult!!.success) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You can continue without a successful test, but transcription won’t work until the server is reachable.",
                color = Amber,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class TestConnectionResult(
    val success: Boolean,
    val message: String,
)

// ---------------------------------------------------------------------------
// Step 4: Enable IME
// ---------------------------------------------------------------------------

@Composable
private fun StepEnableIME(
    onNext: () -> Unit,
    onRegisterResumeCallback: ((() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    var imeEnabled by remember { mutableStateOf(false) }
    var imeSelected by remember { mutableStateOf(false) }

    // Check IME status
    val checkImeStatus = {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val enabledMethods = imm.enabledInputMethodList
        imeEnabled = enabledMethods.any { it.packageName == context.packageName }
        // Check if it's currently selected
        val defaultIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        imeSelected = defaultIme.startsWith(context.packageName)
    }

    // Initial check
    LaunchedEffect(Unit) {
        checkImeStatus()
    }

    // Register resume callback to re-check when returning from settings
    LaunchedEffect(Unit) {
        onRegisterResumeCallback {
            checkImeStatus()
        }
    }

    // Auto-advance when both are done
    LaunchedEffect(imeEnabled, imeSelected) {
        if (imeEnabled && imeSelected) {
            onNext()
        }
    }

    StepContainer {
        Spacer(modifier = Modifier.height(32.dp))

        StepTitle("Turn it on")
        StepBody("Two Android settings switches. We’ll take you there.")

        Spacer(modifier = Modifier.height(24.dp))

        // Step A card
        ImeStepCard(
            label = "Step A",
            title = "Enable LANboard",
            description = "Languages & input → On-screen keyboard",
            isDone = imeEnabled,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step B card
        ImeStepCard(
            label = "Step B",
            title = "Select LANboard",
            description = "Set as your current input method",
            isDone = imeSelected,
        )

        Spacer(modifier = Modifier.height(32.dp))

        PrimaryButton(text = "Open Android settings") {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        Spacer(modifier = Modifier.height(12.dp))

        SecondaryButton(text = "Switch input method") {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm.showInputMethodPicker()
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { onNext() }) {
            Text(
                text = "I’ll do this later",
                color = TextTertiary,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ImeStepCard(label: String, title: String, description: String, isDone: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isDone) GreenPulse.copy(alpha = 0.4f) else Border,
                RoundedCornerShape(12.dp)
            )
            .background(
                if (isDone) GreenPulse.copy(alpha = 0.06f) else ElevatedSurface,
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isDone) GreenPulse.copy(alpha = 0.2f) else TextQuiet.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isDone) "✓" else label.last().toString(),
                    color = if (isDone) GreenPulse else TextTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = if (isDone) GreenPulse else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 5: Try it out
// ---------------------------------------------------------------------------

@Composable
private fun StepTryItOut(onDone: () -> Unit) {
    var tryText by remember { mutableStateOf("") }

    StepContainer {
        Spacer(modifier = Modifier.height(32.dp))

        StepTitle("All set — try it")
        StepBody("Tap the field and hit the mic. Say something. Watch it appear.")

        Spacer(modifier = Modifier.height(32.dp))

        // Try-it text field
        OutlinedTextField(
            value = tryText,
            onValueChange = { tryText = it },
            label = { Text("Try typing or speaking") },
            colors = wizardTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        PrimaryButton(text = "Done", onClick = onDone)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Switch back to my old keyboard? Long-press space",
            color = TextTertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

@Composable
private fun StepContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
}

@Composable
private fun StepBody(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = Background,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextSecondary,
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(BorderBright)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun WizardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isMasked: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextQuiet) },
        colors = wizardTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isMasked) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun wizardTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Primary,
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextTertiary,
    focusedContainerColor = ElevatedSurface,
    unfocusedContainerColor = ElevatedSurface,
)
