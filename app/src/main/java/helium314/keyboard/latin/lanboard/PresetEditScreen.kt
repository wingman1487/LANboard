package helium314.keyboard.latin.lanboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
internal fun PresetEditScreen(navController: NavHostController, presetName: String?) {
    val context = LocalContext.current
    val presetManager = remember { VoicePresetManager(context) }
    val isNew = presetName == null
    val existingPreset = if (!isNew) presetManager.getPresets().find { it.name == presetName } else null
    val activePresetName = presetManager.getActivePreset()?.name

    var name by remember { mutableStateOf(existingPreset?.name ?: "") }
    var promptText by remember { mutableStateOf(existingPreset?.promptText ?: "") }

    val wordCount = if (promptText.isBlank()) 0 else promptText.trim().split(Regex("\\s+")).size
    val wordCountColor = when {
        wordCount > 200 -> LBColors.Red
        wordCount > 150 -> LBColors.Amber
        else -> LBColors.TextTertiary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LBTopBar(
            title = if (isNew) "New preset" else "Edit · $presetName",
            onBack = { navController.popBackStack() }
        )

        // Name field
        LBTextField(
            value = name,
            onValueChange = { name = it },
            label = "Preset name",
            placeholder = "e.g. Medical, Legal, Gaming..."
        )

        Spacer(Modifier.height(12.dp))

        // Prompt text area
        LBTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = "Prompt text",
            placeholder = "Words and phrases to guide Whisper transcription...",
            singleLine = false,
            minLines = 6,
            maxLines = 12
        )

        Spacer(Modifier.height(4.dp))

        // Word counter
        Text(
            "$wordCount / 200 words",
            color = wordCountColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        // Warning callout
        if (wordCount > 200) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LBColors.Red.copy(alpha = 0.12f))
                    .padding(12.dp)
            ) {
                Text(
                    "Long prompts can crowd Whisper's working context and may reduce transcription accuracy. Consider keeping essential terms only.",
                    color = LBColors.Red,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Save / Cancel buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = LBColors.TextSecondary)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Preset name cannot be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isNew) {
                        val existing = presetManager.getPresets().find { it.name == name }
                        if (existing != null) {
                            Toast.makeText(context, "A preset named \"$name\" already exists", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        presetManager.addPreset(VoicePresetManager.Preset(name, promptText))
                    } else {
                        presetManager.updatePreset(presetName, VoicePresetManager.Preset(name, promptText))
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LBColors.Primary,
                    contentColor = LBColors.Background
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        }

        // Delete button (not for active preset)
        if (!isNew && existingPreset != null) {
            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = {
                    if (presetName == activePresetName) {
                        Toast.makeText(context, "Cannot delete the active preset", Toast.LENGTH_SHORT).show()
                    } else {
                        presetManager.deletePreset(presetName)
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LBColors.Red.copy(alpha = 0.7f)
                )
            ) {
                Text("Delete this preset")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
