/*
 * Copyright (C) 2026 LANboard
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.lanboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController

/**
 * §9.3 Manage Presets — list screen. Built to the approved Q2-manage-presets-screen.html (v2.4):
 * a single frosted card of preset rows (identity dot · name · subtitle · drag handle), General last
 * with a PERMANENT marker, then a separate "Add preset" card. Tap a row to edit; drag the handle to
 * reorder. The single global order drives the quick-picker fan order and the per-app cold-start
 * default. The "Auto-select per app" toggle deliberately lives ONLY under Settings → Voice accuracy,
 * never on this screen (§8.1/§9.3).
 */
@Composable
internal fun PresetListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val presetManager = remember { VoicePresetManager(context) }
    val presets = remember { mutableStateListOf<VoicePresetManager.Preset>() }

    fun reload() {
        presets.clear()
        presets.addAll(presetManager.getPresets())
    }

    // (Re)load on entry and whenever we navigate back from the edit screen.
    androidx.compose.runtime.LaunchedEffect(navController.currentBackStackEntry) { reload() }

    val rowHeight = 58.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    // Drag-reorder state. We track the dragged preset by NAME (stable identity) so live swaps inside
    // the list never leave us holding a stale index.
    var draggingName by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LBTopBar(title = "Manage presets", onBack = { navController.popBackStack() })

        Text(
            "Drag to reorder. Order sets the quick-picker fan order and the per-app cold-start default.",
            color = LBColors.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp)
        )

        // ── Preset rows ──
        FrostedCard(modifier = Modifier.fillMaxWidth()) {
            presets.forEachIndexed { index, preset ->
                val isDragged = preset.name == draggingName
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(LBColors.RowDivider)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .fillMaxWidth()
                        .background(if (isDragged) LBColors.ElevatedSurface else Color.Transparent)
                        .clickable { navController.navigate("preset_edit/${preset.name}") }
                        .height(rowHeight)
                        .padding(horizontal = 14.dp)
                ) {
                    // §6.2 identity dot — the preset's color (General's cyan carries a soft glow)
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(Color(preset.colorInt()))
                            .border(
                                3.dp,
                                Color.White.copy(alpha = if (preset.isGeneral()) 0.16f else 0.04f),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            preset.name,
                            color = LBColors.TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (preset.description.isNotBlank()) {
                            Text(
                                preset.description,
                                color = LBColors.TextTertiary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))

                    if (preset.isGeneral()) {
                        // Permanent / non-deletable — no drag handle, a PERMANENT marker instead.
                        Text(
                            "PERMANENT",
                            color = LBColors.TextQuiet,
                            fontSize = 10.sp,
                            letterSpacing = 0.04.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .border(1.dp, LBColors.BorderBright, RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    } else {
                        DragHandle(
                            modifier = Modifier
                                .size(28.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingName = preset.name
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            presetManager.savePresets(presets.toList())
                                            draggingName = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            reload()
                                            draggingName = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val name = draggingName ?: return@detectDragGestures
                                            dragOffset += dragAmount.y
                                            val cur = presets.indexOfFirst { it.name == name }
                                            if (cur < 0) return@detectDragGestures
                                            // swap with a neighbour once we cross half a row, and carry
                                            // the visual offset across so the row stays under the finger
                                            if (dragOffset > rowHeightPx / 2 && cur < presets.lastIndex) {
                                                presets.add(cur + 1, presets.removeAt(cur))
                                                dragOffset -= rowHeightPx
                                            } else if (dragOffset < -rowHeightPx / 2 && cur > 0) {
                                                presets.add(cur - 1, presets.removeAt(cur))
                                                dragOffset += rowHeightPx
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Add preset ──
        FrostedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("preset_new") }
                    .heightIn(min = rowHeight)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Text("+", color = LBColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(12.dp))
                Text("Add preset", color = LBColors.Primary, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** The six-dot grab handle from the render (2 columns × 3 rows), drawn so we need no icon dependency. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val dotColor = Color(0xFF48515D)
    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.045f
        val colGap = size.width * 0.26f
        val rowGap = size.height * 0.24f
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (col in -1..1 step 2) {
            for (row in -1..1) {
                drawCircle(
                    color = dotColor,
                    radius = r,
                    center = Offset(cx + col * colGap / 2f, cy + row * rowGap)
                )
            }
        }
    }
}
