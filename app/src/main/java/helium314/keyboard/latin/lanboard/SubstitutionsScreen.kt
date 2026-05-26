package helium314.keyboard.latin.lanboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
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

@Composable
internal fun SubstitutionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val subManager = remember { SubstitutionManager(context) }
    var rules by remember { mutableStateOf(subManager.getRules()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<SubstitutionManager.Rule?>(null) }
    var dialogHeard by remember { mutableStateOf("") }
    var dialogCorrected by remember { mutableStateOf("") }

    fun refresh() {
        rules = subManager.getRules()
    }

    fun openDialog(rule: SubstitutionManager.Rule?) {
        editingRule = rule
        dialogHeard = rule?.heard ?: ""
        dialogCorrected = rule?.corrected ?: ""
        showDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LBColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LBTopBar(
            title = "Substitutions",
            onBack = { navController.popBackStack() },
            actions = {
                Text(
                    "+",
                    color = LBColors.Primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable { openDialog(null) }
                )
            }
        )

        // Summary
        Text(
            "${rules.size} rules · case-insensitive match",
            color = LBColors.TextTertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Rules list
        rules.forEach { rule ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LBColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        rule.heard,
                        color = LBColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("→", color = LBColors.TextQuiet, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        rule.corrected,
                        color = LBColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    "edit",
                    color = LBColors.Primary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { openDialog(rule) }
                        .padding(start = 12.dp)
                )
            }
        }

        if (rules.isEmpty()) {
            Text(
                "No substitution rules yet. Tap + to add one.",
                color = LBColors.TextQuiet,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    // Edit/Add dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = LBColors.ElevatedSurface,
            title = {
                Text(
                    if (editingRule != null) "Edit substitution" else "Add substitution",
                    color = LBColors.TextPrimary
                )
            },
            text = {
                Column {
                    LBTextField(
                        value = dialogHeard,
                        onValueChange = { dialogHeard = it },
                        label = "Heard (from)",
                        placeholder = "e.g. true nas"
                    )
                    Spacer(Modifier.height(8.dp))
                    LBTextField(
                        value = dialogCorrected,
                        onValueChange = { dialogCorrected = it },
                        label = "Corrected (to)",
                        placeholder = "e.g. TrueNAS"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogHeard.isNotBlank() && dialogCorrected.isNotBlank()) {
                            if (editingRule != null) {
                                subManager.deleteRule(editingRule!!.heard)
                            }
                            subManager.addRule(SubstitutionManager.Rule(dialogHeard.trim(), dialogCorrected.trim()))
                            refresh()
                            showDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LBColors.Primary,
                        contentColor = LBColors.Background
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (editingRule != null) {
                        TextButton(
                            onClick = {
                                subManager.deleteRule(editingRule!!.heard)
                                refresh()
                                showDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = LBColors.Red.copy(alpha = 0.7f)
                            )
                        ) {
                            Text("Delete")
                        }
                    }
                    TextButton(
                        onClick = { showDialog = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = LBColors.TextSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
