package com.example.chaos.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chaos.domain.models.CallEscalation
import com.example.chaos.domain.state.LiraUiState
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.ui.theme.*

@Composable
fun CallsScreen(
    viewModel: LiraViewModel,
    uiState: LiraUiState
) {
    val scrollState = rememberScrollState()

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
                text = "Bolna AI Voice Telephony",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = "Autonomous Voice Agent (Kannada + English) powered by Gemini Brain & ElevenLabs Voice AI.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Bolna Agent Engine Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CyanNeon.copy(alpha = 0.4f))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BOLNA AGENT ORCHESTRATION",
                            fontWeight = FontWeight.Bold,
                            color = CyanGlow,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "LIRA Support Assistant",
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(EmeraldLive.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, EmeraldLive, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("ACTIVE", color = EmeraldLive, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Text(
                    text = "Agent ID: 7c439d93-81bf-447a-a2a7-cfb3fbc3a57f\nDialect: Fluent Kannada (ಕನ್ನಡ) & Indian English Bilingual Voice.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Gemini AI Brain Triage & ElevenLabs Voice Card
        val triage = uiState.latestTriage
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧠 GEMINI AI BRAIN ANALYSIS",
                        fontWeight = FontWeight.Bold,
                        color = CyanNeon,
                        fontSize = 12.sp
                    )

                    if (triage != null) {
                        val sevColor = when (triage.severity) {
                            "HIGH" -> RedAlert
                            "MEDIUM" -> AmberWarning
                            else -> EmeraldLive
                        }
                        Box(
                            modifier = Modifier
                                .background(sevColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, sevColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SEVERITY: ${triage.severity}",
                                color = sevColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                if (triage != null) {
                    Text(
                        text = triage.briefing,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    HorizontalDivider(color = LiraBorder)

                    Text(
                        text = "Bilingual Audio Statement (ElevenLabs):",
                        color = CyanGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\"${triage.spokenAlert}\"",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = { viewModel.playElevenLabsVoice(triage.spokenAlert) },
                        enabled = !uiState.isPlayingVoice,
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = if (uiState.isPlayingVoice) "⏳" else "🔊")
                            Text(
                                text = if (uiState.isPlayingVoice) "Streaming Audio..." else "Speak Bilingual Alert (ElevenLabs)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Trigger a physical sensor edge or start a test call to view real-time Gemini AI triage and ElevenLabs voice generation.",
                        color = SlateMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Approved Contacts Allowlist
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "APPROVED ESCALATION DESTINATIONS",
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                uiState.contacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LiraSurfaceElevated.copy(alpha = 0.6f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = contact.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "${contact.role} • ${contact.phoneMasked}", color = TextSecondary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.startApprovedCall(contact.contactId) },
                            enabled = !uiState.isActionInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Call Bolna", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Dispatch Card
        val activeCall = uiState.activeCall
        if (activeCall != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LiraSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CyanNeon.copy(alpha = 0.5f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JOB: ${activeCall.jobId}",
                            fontWeight = FontWeight.Black,
                            color = CyanNeon,
                            fontSize = 16.sp
                        )

                        val statusColor = when (activeCall.status) {
                            "in_progress", "dialing" -> AmberWarning
                            "completed" -> EmeraldLive
                            "failed", "no_answer" -> RedAlert
                            else -> CyanGlow
                        }

                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = activeCall.status.uppercase(),
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "Recipient: ${activeCall.contactName} (${activeCall.contactPhoneMasked})",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )

                    Text(
                        text = "Orchestrator: Bolna AI (Agent: 7c439d93)",
                        color = CyanGlow,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Attempt: ${activeCall.attempt} of ${uiState.policy.maxCallsPerIncident}", color = TextMuted, fontSize = 12.sp)
                        if (activeCall.durationSeconds != null) {
                            Text(text = "Duration: ${activeCall.durationSeconds}s", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    if (!activeCall.transcriptSummary.isNullOrBlank()) {
                        HorizontalDivider(color = LiraBorder)
                        Text(text = "Bolna Verified Transcript:", color = CyanGlow, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(text = activeCall.transcriptSummary, color = TextPrimary, fontSize = 12.sp)
                    }

                    if (!activeCall.transcript.isNullOrBlank()) {
                        HorizontalDivider(color = LiraBorder)
                        Text(text = "Full Bilingual Dialogue:", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(text = activeCall.transcript, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                    }

                    if (!activeCall.error.isNullOrBlank()) {
                        HorizontalDivider(color = LiraBorder)
                        Text(text = "Telephony Notes:", color = RedAlert, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(text = activeCall.error, color = RedAlert, fontSize = 12.sp)
                    }
                }
            }
        }

        // Call History
        if (uiState.callHistory.isNotEmpty()) {
            Text(
                text = "PAST ESCALATION DISPATCHES",
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )

            uiState.callHistory.forEach { call ->
                PastCallCard(call = call)
            }
        }
    }
}

@Composable
fun PastCallCard(call: CallEscalation) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LiraSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LiraBorder)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = call.jobId, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = call.status.uppercase(),
                    color = if (call.status == "completed") EmeraldLive else RedAlert,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(text = "To: ${call.contactName} • Incident: ${call.incidentId}", color = TextSecondary, fontSize = 12.sp)
        }
    }
}
