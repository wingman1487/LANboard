package helium314.keyboard.latin.lanboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import helium314.keyboard.latin.BuildConfig

@Composable
internal fun AboutScreen(navController: NavHostController) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LBTopBar(
            title = "Credits & licenses",
            onBack = { navController.popBackStack() }
        )

        // Attribution
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LBColors.Surface)
                .padding(16.dp)
        ) {
            Text(
                "Attribution",
                color = LBColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                "LANboard is a fork of HeliBoard, which is a fork of OpenBoard, " +
                    "which is a fork of AOSP LatinIME.",
                color = LBColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(16.dp))

            // HeliBoard link
            Text(
                "HeliBoard on GitHub",
                color = LBColors.Primary,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Helium314/HeliBoard"))
                    )
                }
            )

            Spacer(Modifier.height(8.dp))

            // OpenBoard link
            Text(
                "OpenBoard on GitHub",
                color = LBColors.Primary,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/openboard-team/openboard"))
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // License
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LBColors.Surface)
                .padding(16.dp)
        ) {
            Text(
                "License",
                color = LBColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                "GNU General Public License v3.0",
                color = LBColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                GPL_V3_SUMMARY,
                color = LBColors.TextTertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Full license text",
                color = LBColors.Primary,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.gnu.org/licenses/gpl-3.0.en.html")
                        )
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Version
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LBColors.Surface)
                .padding(16.dp)
        ) {
            Text(
                "LANboard",
                color = LBColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                color = LBColors.TextTertiary,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

private const val GPL_V3_SUMMARY = """This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see the link below."""
