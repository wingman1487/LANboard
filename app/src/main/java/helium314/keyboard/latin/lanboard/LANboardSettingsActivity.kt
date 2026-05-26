package helium314.keyboard.latin.lanboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class LANboardSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lanboardDarkScheme) {
                SettingsScreen()
            }
        }
    }
}

private val lanboardDarkScheme = darkColorScheme(
    background = Color(0xFF0A0D10),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF1C232C),
    primary = Color(0xFF00D4FF),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF9BA8B4),
)

@Composable
private fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D10))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "LANboard Settings",
            color = Color(0xFFE6EDF3),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSection("Server") {
            Text("Server URL, connection, model, language, auth", color = Color(0xFF6B7785), fontSize = 14.sp)
        }

        SettingsSection("Voice Accuracy") {
            Text("Active preset, manage presets, substitutions", color = Color(0xFF6B7785), fontSize = 14.sp)
        }

        SettingsSection("Keyboard") {
            Text("Terminal row default, sensitive fields, HeliBoard settings", color = Color(0xFF6B7785), fontSize = 14.sp)
        }

        SettingsSection("Storage") {
            Text("Pending recordings", color = Color(0xFF6B7785), fontSize = 14.sp)
        }

        SettingsSection("About") {
            Text("Credits & licenses, version", color = Color(0xFF6B7785), fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            color = Color(0xFF00D4FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}
