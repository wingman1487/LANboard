// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lanboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The single §6.1 LANboard Dark token source, shared by every Compose surface — the LANboard-native
 * settings (LANboardSettingsActivity + its sibling screens) AND the re-skinned inherited HeliBoard
 * settings (via [helium314.keyboard.latin.utils.Theme]). §6.1 mandates one token source spanning both
 * domains; these values mirror res/values/colors_lanboard.xml (the View/Canvas keyboard side) exactly.
 *
 * Visual reference for the settings surface: Q6-settings-reskin-screen.html (§6.6 restrained frosted glass).
 */
internal object LBColors {
    // Backgrounds
    val Background = Color(0xFF0A0D10)      // lb_bg_deepest
    val BackgroundDeep = Color(0xFF0F1318)  // lb_bg_deep
    val Surface = Color(0xFF161B22)         // lb_bg_mid
    val ElevatedSurface = Color(0xFF1C232C) // lb_bg_elev
    val ElevatedSurface2 = Color(0xFF232B35)// lb_bg_elev_2

    // Frosted card gradient (Q6-settings-reskin-screen.html .card rule)
    val CardTop = Color(0xFF1A2029)
    val CardBottom = Color(0xFF13181F)

    // Borders
    val Border = Color(0xFF2A323C)          // lb_border
    val BorderBright = Color(0xFF3A4451)    // lb_border_bright
    val ScreenBorder = Color(0xFF20262E)    // .scr border in the mockup

    // Text hierarchy
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF9BA8B4)
    val TextTertiary = Color(0xFF6B7785)
    val TextQuiet = Color(0xFF4A5360)

    // Active accents
    val Primary = Color(0xFF00D4FF)         // lb_cyan
    val CyanSoft = Color(0xFF4DD9EE)        // lb_cyan_soft
    val ElectricBlue = Color(0xFF2B7FFF)
    val DeepBlue = Color(0xFF1851D4)
    val GreenPulse = Color(0xFF3DDC97)
    val Amber = Color(0xFFF0B429)
    val Red = Color(0xFFEF4444)

    // Hairline divider between rows inside a frosted card (mockup .rw border-bottom)
    val RowDivider = Color(0x0AFFFFFF)      // rgba(255,255,255,0.04)
    val CardTopHighlight = Color(0x0FFFFFFF)// rgba(255,255,255,0.06) inset top highlight
}

/**
 * The fixed §6.1 LANboard Dark Material3 color scheme. Installed as the default for the inherited
 * HeliBoard settings surface (replacing the Material You dynamic-color path) and used directly by the
 * LANboard-native settings activity. Maps §6.1 tokens onto Material3 roles so stock Material3 controls
 * (Switch/Slider/Checkbox/RadioButton/Button) pick up cyan via primary, and surfaces resolve to LANboard
 * dark tokens rather than the device wallpaper palette.
 */
internal val lanboardDarkScheme = darkColorScheme(
    primary = LBColors.Primary,
    onPrimary = LBColors.Background,
    primaryContainer = LBColors.DeepBlue,
    onPrimaryContainer = LBColors.TextPrimary,
    secondary = LBColors.CyanSoft,
    onSecondary = LBColors.Background,
    secondaryContainer = LBColors.ElevatedSurface,
    onSecondaryContainer = LBColors.TextPrimary,
    tertiary = LBColors.CyanSoft,
    onTertiary = LBColors.Background,
    background = LBColors.Background,
    onBackground = LBColors.TextPrimary,
    surface = LBColors.Background,
    onSurface = LBColors.TextPrimary,
    surfaceVariant = LBColors.Surface,
    onSurfaceVariant = LBColors.TextSecondary,
    surfaceTint = LBColors.Primary,
    // Appbar / scaffold containers: blend the top app bar into the screen as in the mockup,
    // step up toward the elevated surfaces for higher containers.
    surfaceContainerLowest = LBColors.Background,
    surfaceContainerLow = LBColors.BackgroundDeep,
    surfaceContainer = LBColors.Background,
    surfaceContainerHigh = LBColors.Surface,
    surfaceContainerHighest = LBColors.ElevatedSurface,
    outline = LBColors.Border,
    outlineVariant = LBColors.ScreenBorder,
    error = LBColors.Red,
    onError = LBColors.Background,
    scrim = Color(0xCC000000),
)

/**
 * Restrained frosted-glass card (§6.6): a gentle vertical gradient, a soft §6.1 border, and a 1px inset
 * top highlight — NOT the keyboard's full 3D glass (§6.7). Static (no live blur / no ambient sheen) to
 * honor §13.2. Mirrors the .card rule in Q6-settings-reskin-screen.html. Rows go inside as children;
 * callers add hairline [LBColors.RowDivider] separators between rows.
 */
@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(LBColors.CardTop, LBColors.CardBottom)))
            .border(1.dp, LBColors.Border, shape)
            .drawWithContent {
                drawContent()
                // inset top highlight — the glass edge catch
                val y = 0.5.dp.toPx()
                drawLine(
                    color = LBColors.CardTopHighlight,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        content = content,
    )
}

/**
 * Cyan-soft uppercase section header above a [FrostedCard] (mockup .sect rule: 11sp, .08em tracking,
 * cyan-soft). Shared so the inherited HeliBoard PreferenceCategory and LANboard-native sections read
 * identically.
 */
@Composable
fun LBSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
        color = LBColors.CyanSoft,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em,
    )
}
