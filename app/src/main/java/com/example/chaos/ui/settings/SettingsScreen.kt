package com.example.chaos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chaos.domain.state.LiraUiState
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: LiraViewModel,
    uiState: LiraUiState
) {
    val scrollState = rememberScrollState()
    var autoEscalation by remember(uiState.policy.autoEscalation) { mutableStateOf(uiState.policy.autoEscalation) }
    var maxCalls by remember(uiState.policy.maxCallsPerIncident) { mutableStateOf(uiState.policy.maxCallsPerIncident.toString()) }
    var ackTimeout by remember(uiState.policy.acknowledgementTimeoutSeconds) { mutableStateOf(uiState.policy.acknowledgementTimeoutSeconds.toString()) }
    var cooldown by remember(uiState.policy.duplicateIncidentCooldownSeconds) { mutableStateOf(uiState.policy.duplicateIncidentCooldownSeconds.toString()) }
    var relayAuto by remember(uiState.policy.relayAutomation) { mutableStateOf(uiState.policy.relayAutomation) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiraBackground)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Automation & Safety Guardrails",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = "Strict deterministic boundaries for AI escalation and physical triggers.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Escalation Outbound Calling",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Automatically dispatches phone call to supervisor if incident is unacknowledged past timeout.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoEscalation,
                        onCheckedChange = { autoEscalation = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanNeon,
                            checkedTrackColor = SkyBlue
                        )
                    )
                }

                if (autoEscalation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EmeraldLive.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, EmeraldLive.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Policy Active: Strict Telephony Bound",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLive,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• Destination: Approved Whitelisted Supervisor only.\n• Hard Cap: Maximum 2 calls per incident.\n• Non-emergency: Emergency dialers strictly prohibited.",
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = LiraBorder)

                OutlinedTextField(
                    value = maxCalls,
                    onValueChange = { maxCalls = it },
                    label = { Text("Max Calls Per Incident (Cap: 2)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ackTimeout,
                    onValueChange = { ackTimeout = it },
                    label = { Text("Demo Ack Timeout (Seconds)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cooldown,
                    onValueChange = { cooldown = it },
                    label = { Text("Duplicate Cooldown Window (Seconds)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Relay Automation Output",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Optional 4-channel relay. Demo is restricted to low-voltage indicator loads.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = relayAuto,
                        onCheckedChange = { relayAuto = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanNeon,
                            checkedTrackColor = SkyBlue
                        )
                    )
                }

                Button(
                    onClick = {
                        val mCalls = maxCalls.toIntOrNull() ?: 1
                        val aTimeout = ackTimeout.toIntOrNull() ?: 20
                        val cCooldown = cooldown.toIntOrNull() ?: 60
                        viewModel.updatePolicy(
                            autoEscalation = autoEscalation,
                            maxCalls = mCalls,
                            ackTimeoutSec = aTimeout,
                            duplicateCooldownSec = cCooldown,
                            relayAutomation = relayAuto
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Policy Guardrails", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
