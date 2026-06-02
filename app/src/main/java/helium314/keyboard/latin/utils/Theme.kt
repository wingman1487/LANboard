// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import helium314.keyboard.latin.lanboard.lanboardDarkScheme

// LANboard (§6.6): the inherited HeliBoard settings surface wears the fixed §6.1 LANboard Dark scheme
// (shared token source: latin/lanboard/LANboardTheme.kt) — NOT Material You dynamic color, which derived
// the palette from the device wallpaper and contradicted the locked cyan identity (§6.1/§6.6). Style only;
// HeliBoard's preference logic is untouched. `dark` is retained for API compatibility (LANboard authors
// dark only — LANboard Light is a future-maybe per §6.1).
@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val material3 = Typography()
    val colorScheme = lanboardDarkScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            titleLarge = material3.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = material3.titleMedium.copy(fontWeight = FontWeight.Bold),
            titleSmall = material3.titleSmall.copy(fontWeight = FontWeight.Bold)
        ),
        //shapes = Shapes(),
        content = content
    )
}

const val previewDark = true
