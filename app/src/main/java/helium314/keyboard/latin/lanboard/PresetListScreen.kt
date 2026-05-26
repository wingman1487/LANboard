package helium314.keyboard.latin.lanboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
internal fun PresetListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val presetManager = remember { VoicePresetManager(context) }
    var presets by remember { mutableStateOf(presetManager.getPresets()) }
    var activePresetName by remember { mutableStateOf(presetManager.getActivePreset()?.name ?: "General") }

    fun refresh() {
        presets = presetManager.getPresets()
        activePresetName = presetManager.getActivePreset()?.name ?: "General"
    }

    // Refresh when navigating back from edit
    androidx.compose.runtime.LaunchedEffect(navController.currentBackStackEntry) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LBTopBar(
            title = "Voice presets",
            onBack = { navController.popBackStack() },
            actions = {
                Text(
                    "+",
                    color = LBColors.Primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable {
                        navController.navigate("preset_new")
                    }
                )
            }
        )

        // Summary
        Text(
            "${presets.size} presets · 1 active",
            color = LBColors.TextTertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Preset cards
        presets.forEach { preset ->
            val isActive = preset.name == activePresetName
            val wordCount = preset.wordCount()
            val wordColor = when {
                wordCount > 200 -> LBColors.Red
                wordCount > 150 -> LBColors.Amber
                else -> LBColors.TextTertiary
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LBColors.Surface)
                    .clickable {
                        navController.navigate("preset_edit/${preset.name}")
                    }
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Active marker dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isActive) LBColors.Primary else LBColors.Border)
                            .clickable {
                                presetManager.setActivePreset(preset.name)
                                refresh()
                            }
                    )
                    Spacer(Modifier.width(10.dp))

                    // Name
                    Text(
                        preset.name,
                        color = LBColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )

                    // Word count
                    Text(
                        "${wordCount}/200 words",
                        color = wordColor,
                        fontSize = 12.sp
                    )
                }

                // Preview
                if (preset.promptText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        preset.promptText,
                        color = LBColors.TextQuiet,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
