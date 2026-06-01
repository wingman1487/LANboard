package helium314.keyboard.latin.lanboard

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
internal fun PendingQueueScreen(navController: NavHostController) {
    val context = LocalContext.current
    val pendingManager = remember { PendingRecordingsManager(context) }
    var recordings by remember { mutableStateOf(pendingManager.getPendingRecordings()) }

    fun refresh() {
        recordings = pendingManager.getPendingRecordings()
    }

    val totalCount = recordings.size
    val totalSizeBytes = recordings.sumOf { it.wavFile.length() }
    val totalSizeKb = totalSizeBytes / 1024
    val oldestAgoMs = if (recordings.isNotEmpty()) {
        System.currentTimeMillis() - recordings.minOf { it.createdAt }
    } else 0L
    val oldestAgoHours = TimeUnit.MILLISECONDS.toHours(oldestAgoMs)
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LBTopBar(
            title = "Pending recordings",
            onBack = { navController.popBackStack() }
        )

        if (totalCount > 0) {
            // Summary row
            Text(
                "$totalCount recordings · ${totalSizeKb}KB total · oldest ${oldestAgoHours}h ago",
                color = LBColors.TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Retry all button
            Button(
                onClick = {
                    // Retry is handled by the voice pipeline; here we just refresh
                    refresh()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LBColors.ElevatedSurface,
                    contentColor = LBColors.Primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Retry all")
            }

            Spacer(Modifier.height(12.dp))

            // Recording cards
            recordings.forEach { recording ->
                val timestamp = dateFormat.format(Date(recording.createdAt))
                val fileSizeKb = recording.wavFile.length() / 1024
                val isTranscribed = recording.transcription != null
                val statusText = if (isTranscribed) "Transcribed" else "Queued"
                val statusColor = if (isTranscribed) LBColors.GreenPulse else LBColors.Amber

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(LBColors.Surface)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            timestamp,
                            color = LBColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Status pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "${fileSizeKb}KB",
                        color = LBColors.TextTertiary,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Copy button — explicit clipboard recovery for a transcribed queue item
                        // (§5.5: queue items are NOT auto-copied; they keep the explicit Copy button
                        // with a confirmation toast).
                        if (isTranscribed) {
                            TextButton(
                                onClick = {
                                    recording.transcription?.let { text ->
                                        LANboardClipboard.copy(context, text)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Copied to clipboard",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = LBColors.Primary
                                )
                            ) {
                                Text("Copy", fontSize = 13.sp)
                            }

                            Spacer(Modifier.width(8.dp))
                        }

                        TextButton(
                            onClick = {
                                pendingManager.deletePending(recording.id)
                                refresh()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = LBColors.Red.copy(alpha = 0.7f)
                            )
                        ) {
                            Text("Discard", fontSize = 13.sp)
                        }

                        Spacer(Modifier.width(8.dp))

                        TextButton(
                            onClick = {
                                // Retry is handled by the pipeline; refresh state
                                refresh()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = LBColors.Primary
                            )
                        ) {
                            Text("Retry", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Auto-cleanup note
            Text(
                "Pending recordings older than 7 days are deleted automatically.",
                color = LBColors.TextQuiet,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        } else {
            // Empty state
            Spacer(Modifier.height(48.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No pending recordings",
                    color = LBColors.TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "When everything's working, this screen stays empty.",
                    color = LBColors.TextQuiet,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
