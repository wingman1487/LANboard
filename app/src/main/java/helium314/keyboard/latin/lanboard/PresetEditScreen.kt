/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import helium314.keyboard.latin.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * §9.3 Preset edit screen, built to Q2-manage-presets-screen.html (v2.4). Two variants:
 *  - non-General: NAME · PROMPT (with a soft ~150-word counter) · the Preset color block (curated
 *    swatch grid + live mic preview) · a low-emphasis Delete action.
 *  - General (the exception): NAME · PROMPT, then a fixed cyan identity chip instead of the swatch
 *    grid, and no Delete — General owns cyan and is permanent (§6.2/§8.1).
 */
@Composable
internal fun PresetEditScreen(navController: NavHostController, presetName: String?) {
    val context = LocalContext.current
    val presetManager = remember { VoicePresetManager(context) }
    val isNew = presetName == null
    val existingPreset = if (!isNew) presetManager.getPresets().find { it.name == presetName } else null
    val isGeneral = !isNew && LBPresetPalette.isGeneral(presetName ?: "")
    val allPresets = remember { presetManager.getPresets() }

    var name by remember { mutableStateOf(existingPreset?.name ?: "") }
    var promptText by remember { mutableStateOf(existingPreset?.promptText ?: "") }
    var selectedColor by remember {
        mutableStateOf(
            when {
                isGeneral -> LBPresetPalette.GENERAL_HEX
                isNew -> LBPresetPalette.NEW_PRESET_HEX
                else -> existingPreset?.colorHex ?: LBPresetPalette.NEW_PRESET_HEX
            }
        )
    }

    val wordCount = if (promptText.isBlank()) 0 else promptText.trim().split(Regex("\\s+")).size
    val wordCountColor = if (wordCount >= 150) LBColors.Amber else LBColors.TextTertiary

    fun commit() {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(context, "Preset name can't be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (isNew) {
            if (presetManager.getPresets().any { it.name.equals(trimmed, ignoreCase = true) }) {
                Toast.makeText(context, "A preset named \"$trimmed\" already exists", Toast.LENGTH_SHORT).show()
                return
            }
            presetManager.addPreset(
                VoicePresetManager.Preset(name = trimmed, promptText = promptText, colorHex = selectedColor)
            )
        } else {
            presetManager.updatePreset(
                presetName!!,
                VoicePresetManager.Preset(
                    name = if (isGeneral) LBPresetPalette.GENERAL_NAME else trimmed,
                    promptText = promptText,
                    isDefault = existingPreset?.isDefault ?: false,
                    colorHex = if (isGeneral) LBPresetPalette.GENERAL_HEX else selectedColor,
                    description = existingPreset?.description ?: ""
                )
            )
        }
        navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LBTopBar(
            title = if (isNew) "New preset" else "Edit preset",
            onBack = { navController.popBackStack() },
            actions = {
                Text(
                    "Done",
                    color = LBColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { commit() }
                )
            }
        )

        // ── NAME ──
        if (isGeneral) {
            // General's identity is fixed — its name anchors the universal fallback lookups, so it is
            // shown but not editable. (Prompt remains fully editable.)
            Text("NAME", color = LBColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LBColors.Surface)
                    .border(1.dp, LBColors.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 13.dp)
            ) {
                Text("General", color = LBColors.TextSecondary, fontSize = 14.sp)
            }
        } else {
            LBTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                placeholder = "e.g. Medical, Legal, Gaming…"
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── PROMPT ──
        LBTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = "Prompt",
            placeholder = "Words and phrases to bias Whisper toward this context…",
            singleLine = false,
            minLines = 4,
            maxLines = 12
        )
        Text(
            "$wordCount / ~150 words",
            color = wordCountColor,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, end = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        Spacer(Modifier.height(16.dp))

        // ── Preset color ──
        if (isGeneral) {
            GeneralFixedColorChip()
        } else {
            PresetColorBlock(
                selectedColor = selectedColor,
                onSelect = { selectedColor = it },
                otherUsers = allPresets.filter { it.name != presetName && it.colorHex.equals(selectedColor, ignoreCase = true) }
                    .map { it.name }
            )
        }

        // ── Delete (non-General only) ──
        if (!isNew && !isGeneral && existingPreset != null) {
            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF7A2828), RoundedCornerShape(10.dp))
                        .clickable {
                            presetManager.deletePreset(presetName!!)
                            navController.popBackStack()
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Delete preset", color = Color(0xFFC46060), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/** The curated swatch grid + live mic preview + reserved-hues footnote (non-General edit). */
@Composable
private fun PresetColorBlock(
    selectedColor: String,
    onSelect: (String) -> Unit,
    otherUsers: List<String>,
) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Preset color", color = LBColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("tints the mic icon + spike tips", color = LBColors.TextTertiary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                // 6×2 swatch grid
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LBPresetPalette.CURATED_SWATCHES.chunked(6).forEach { rowHexes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowHexes.forEach { hex ->
                                Swatch(
                                    hex = hex,
                                    selected = hex.equals(selectedColor, ignoreCase = true),
                                    isLockedDefault = LBPresetPalette.LOCKED_DEFAULT_HEXES.any { it.equals(hex, ignoreCase = true) },
                                    onClick = { onSelect(hex) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                // Live mic preview
                Column(
                    modifier = Modifier.width(92.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MicPreview(color = Color(LBPresetPalette.parseOrDefault(selectedColor)))
                    Spacer(Modifier.height(4.dp))
                    Text("Live preview", color = LBColors.TextTertiary, fontSize = 10.sp)
                }
            }

            if (otherUsers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Also used by ${otherUsers.joinToString(", ")}",
                    color = LBColors.TextTertiary,
                    fontSize = 11.sp
                )
            }

            // Reserved-hues footnote
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LBColors.RowDivider))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cyan is reserved for General. Status hues stay locked out:",
                    color = LBColors.TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0xFF3DDC97, 0xFFF0B429, 0xFFEF4444, 0xFF00D4FF).forEach { c ->
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c)))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("— plus purple.", color = LBColors.TextQuiet, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Swatch(
    hex: String,
    selected: Boolean,
    isLockedDefault: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(LBPresetPalette.parseOrDefault(hex)))
                .then(
                    if (selected) Modifier.border(2.dp, LBColors.Primary, CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick)
        )
        if (selected) {
            // check mark
            Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (isLockedDefault) {
            // notch dot bottom-right marking a locked default
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LBColors.Background)
                    .border(1.dp, LBColors.TextQuiet, CircleShape)
            )
        }
    }
}

/** Static 3-spike mic preview (cyan base → preset tip), mirroring the §6.2 listening render. */
@Composable
private fun MicPreview(color: Color) {
    val cyan = LBColors.Primary
    Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r0 = size.minDimension * 0.28f         // just outside the inner-circle perimeter
            val stroke = size.minDimension * 0.04f
            // angle (deg from straight up, clockwise), length fraction — from the render
            val spikes = listOf(-32f to 0.13f, 8f to 0.19f, 40f to 0.11f)
            spikes.forEach { (deg, lenFrac) ->
                val rad = Math.toRadians(deg.toDouble())
                val dir = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
                val base = center + dir * r0
                val tip = center + dir * (r0 + size.minDimension * lenFrac)
                drawLine(
                    brush = Brush.linearGradient(listOf(cyan, color), start = base, end = tip),
                    start = base,
                    end = tip,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
        // inner circle + mic glyph
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(2.dp, cyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic_none),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** General's fixed cyan identity chip — no swatch grid; cyan can't be recolored (§6.2/§8.1). */
@Composable
private fun GeneralFixedColorChip() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Preset color", color = LBColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("fixed", color = LBColors.TextTertiary, fontSize = 11.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF10242C))
                .border(1.dp, LBColors.Primary.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(LBColors.Primary)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Always cyan — the signature", color = LBColors.CyanSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "General owns LANboard's cyan; it can't be recolored.",
                    color = LBColors.TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
