package com.example.chaos.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chaos.domain.models.IncidentState
import com.example.chaos.domain.state.LiraUiState
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: LiraViewModel,
    uiState: LiraUiState
) {
    val scrollState = rememberScrollState()
    val isDeviceOnline = uiState.deviceStatus.uptimeSeconds > 0
    val isArmed = uiState.deviceStatus.armed
    val currentInc = uiState.deviceStatus.currentIncident
    val isAwaitingAck = currentInc?.state == IncidentState.AWAITING_ACK

    // Pulsing animation for active guard
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiraBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Futuristic Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LIRA",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(CyanNeon.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CORE v${uiState.deviceStatus.firmwareVersion}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanNeon
                        )
                    }
                }
                Text(
                    text = "LINKED INTELLIGENCE & RESPONSE AGENT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyanGlow,
                    letterSpacing = 0.8.sp
                )
            }

            // Live Connection Beacon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(LiraSurfaceElevated, RoundedCornerShape(20.dp))
                    .border(1.dp, if (isDeviceOnline) EmeraldLive.copy(alpha = 0.5f) else RedAlert.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDeviceOnline) EmeraldLive else RedAlert)
                )
                Text(
                    text = if (isDeviceOnline) "NODE ACTIVE" else "STANDALONE",
                    color = if (isDeviceOnline) EmeraldLive else RedAlert,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // Prominent Hackathon Showcase Demo Mode Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(CyanNeon, AmberWarning))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "⚡ HACKATHON SHOWCASE",
                                color = AmberWarning,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyanNeon.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "INTERACTIVE",
                                    color = CyanNeon,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = "Simulate visitor flows, AI voice call & auto-email report.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = { viewModel.startDemoMode() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (uiState.isDemoModeActive) "Open Demo" else "Start Demo",
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A),
                            fontSize = 12.sp
                        )
                    }
                }

                if (uiState.demoInitStep != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "▶ ${uiState.demoInitStep}",
                            color = CyanNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Mission Control Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(LiraSurfaceElevated, LiraSurface)
                    )
                )
                .border(1.dp, LiraBorder, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PERIMETER STATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isArmed) "ARMED • GUARD ACTIVE" else "DISARMED • STANDBY",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isArmed) CyanNeon else SlateMuted
                        )
                    }

                    // Pulsing radar orb
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(if (isArmed) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(if (isArmed) CyanNeon.copy(alpha = 0.2f) else SlateMuted.copy(alpha = 0.15f))
                            .border(1.5.dp, if (isArmed) CyanNeon else SlateMuted, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isArmed) CyanNeon else SlateMuted)
                        )
                    }
                }

                HorizontalDivider(color = LiraBorder.copy(alpha = 0.6f))

                // State Machine Progress Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Deterministic State Machine",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = uiState.deviceStatus.state.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = when (uiState.deviceStatus.state) {
                            IncidentState.ARMED_IDLE -> CyanGlow
                            IncidentState.INCIDENT_DETECTED, IncidentState.AWAITING_ACK -> AmberWarning
                            IncidentState.CALLING, IncidentState.ESCALATION_PENDING -> RedAlert
                            IncidentState.RESOLVED, IncidentState.ACKNOWLEDGED -> EmeraldLive
                            else -> SlateMuted
                        }
                    )
                }

                // Arm / Disarm Toggle Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.setArmState(true) },
                        enabled = !isArmed && !uiState.isActionInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBlue,
                            disabledContainerColor = SkyBlue.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🛡️ Arm Guard", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = { viewModel.setArmState(false) },
                        enabled = isArmed && !uiState.isActionInProgress,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(LiraBorder, LiraBorder))
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disarm", color = if (isArmed) TextPrimary else SlateMuted)
                    }
                }
            }
        }

        // Active Incident Alert & Real-Time Countdown
        if (currentInc != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isAwaitingAck) AmberWarning.copy(alpha = 0.12f) else LiraSurfaceElevated
                    )
                    .border(
                        1.5.dp,
                        if (isAwaitingAck) AmberWarning else CyanGlow,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "⚠️", fontSize = 18.sp)
                            Column {
                                Text(
                                    text = "INCIDENT ${currentInc.incidentId}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "TRIGGER: ${currentInc.type.replace('_', ' ').uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning
                                )
                            }
                        }

                        if (isAwaitingAck) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${uiState.ackCountdownSeconds}s",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AmberWarning,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(text = "TIMEOUT", fontSize = 9.sp, color = TextSecondary)
                            }
                        }
                    }

                    if (isAwaitingAck) {
                        // Countdown Progress Bar
                        val maxTimeout = uiState.policy.acknowledgementTimeoutSeconds.coerceAtLeast(1)
                        val progress = (uiState.ackCountdownSeconds.toFloat() / maxTimeout).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (progress > 0.4f) EmeraldLive else RedAlert,
                            trackColor = LiraBorder
                        )
                    }

                    // Acknowledge Button
                    Button(
                        onClick = { viewModel.acknowledgeCurrentIncident("app") },
                        enabled = isAwaitingAck && !uiState.isActionInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Acknowledge Physical Event (Operator)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF042F2E)
                        )
                    }
                }
            }
        }

        // Live Sensor Telemetry (Section 4 Pinout)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(LiraBorder, LiraBorder))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "HARDWARE TELEMETRY NODES",
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                SensorTelemetryRow(
                    label = "PIR Motion HC-SR501",
                    pin = "GPIO 27",
                    isActive = uiState.deviceStatus.sensors["pir"]?.active == true
                )

                SensorTelemetryRow(
                    label = "HW-072 Vibration Sensor",
                    pin = "GPIO 34",
                    isActive = uiState.deviceStatus.sensors["vibration"]?.active == true
                )

                SensorTelemetryRow(
                    label = "RC522 RFID SPI Reader",
                    pin = "GPIO 21 (CS)",
                    isActive = false,
                    isReady = uiState.deviceStatus.sensors["rc522"]?.ready == true,
                    customTag = if (uiState.deviceStatus.sensors["rc522"]?.ready == true) "ONLINE" else "STANDBY"
                )
            }
        }

        // Interactive Simulation Bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurfaceElevated.copy(alpha = 0.7f)),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanNeon.copy(alpha = 0.3f), LiraBorder))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎮 Interactive Demonstration Controls",
                        fontWeight = FontWeight.Bold,
                        color = CyanNeon,
                        fontSize = 13.sp
                    )
                    Text(text = "LIVE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = EmeraldLive)
                }
                Text(
                    text = "Tap to test physical sensor edges & tag verification instantly:",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.simulateSensorEdge("motion") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Text("🏃 Trip PIR", fontSize = 11.sp, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = { viewModel.simulateSensorEdge("vibration") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Text("📳 Trip Vibration", fontSize = 11.sp, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = { viewModel.simulateTagTap("4A:5B:6C:7D") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Text("🏷️ Tap Tag", fontSize = 11.sp, color = CyanGlow)
                    }
                }
            }
        }

        // Primary Voice Escalation Test Button
        val primaryContact = uiState.contacts.firstOrNull()
        Button(
            onClick = { primaryContact?.let { viewModel.startApprovedCall(it.contactId) } },
            enabled = primaryContact != null && !uiState.isActionInProgress,
            colors = ButtonDefaults.buttonColors(
                containerColor = SkyBlue,
                disabledContainerColor = SkyBlue.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(text = "📞", fontSize = 18.sp)
                Text(
                    text = "Dispatch Approved AI Call (${primaryContact?.name ?: "Supervisor"})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Action Status Banner
        if (uiState.lastActionMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LiraSurfaceElevated)
                    .border(1.dp, LiraBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = uiState.lastActionMessage,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun SensorTelemetryRow(label: String, pin: String, isActive: Boolean, isReady: Boolean = true, customTag: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LiraBackground.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = pin, color = TextMuted, fontSize = 11.sp)
        }

        val tagColor = when {
            isActive -> RedAlert
            customTag != null -> if (isReady) EmeraldLive else SlateMuted
            else -> EmeraldLive
        }

        Box(
            modifier = Modifier
                .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .border(1.dp, tagColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = customTag ?: if (isActive) "TRIGGERED" else "NORMAL",
                color = tagColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
