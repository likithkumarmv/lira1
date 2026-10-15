package com.example.chaos.ui.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.chaos.domain.models.Incident
import com.example.chaos.domain.models.IncidentState
import com.example.chaos.domain.state.LiraUiState
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.ui.theme.*

@Composable
fun IncidentsScreen(
    viewModel: LiraViewModel,
    uiState: LiraUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiraBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Incident Provenance Ledger",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = "Tamper-evident audit trail distinguishing Sensor Events, Human Acks, and Cloud Escalations.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        if (uiState.incidents.isEmpty() && uiState.deviceStatus.currentIncident == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recorded incidents yet.\nArm device and trigger PIR or vibration to record physical events.",
                    color = SlateMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val currentInc = uiState.deviceStatus.currentIncident
                if (currentInc != null) {
                    item {
                        IncidentCard(
                            incident = Incident(
                                incidentId = currentInc.incidentId,
                                type = currentInc.type,
                                state = currentInc.state,
                                evidence = listOf("live_sensor_edge", "awaiting_local_response")
                            ),
                            isCurrent = true
                        )
                    }
                }

                items(uiState.incidents) { inc ->
                    IncidentCard(incident = inc, isCurrent = false)
                }
            }
        }
    }
}

@Composable
fun IncidentCard(incident: Incident, isCurrent: Boolean) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) LiraSurfaceElevated else LiraSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isCurrent) AmberWarning.copy(alpha = 0.6f) else LiraBorder)
        ),
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
                        text = incident.incidentId,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "TRIGGER: ${incident.type.replace('_', ' ').uppercase()}",
                        color = CyanGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val statusColor = when (incident.state) {
                    IncidentState.AWAITING_ACK -> AmberWarning
                    IncidentState.CALLING, IncidentState.ESCALATION_PENDING -> RedAlert
                    IncidentState.RESOLVED, IncidentState.ACKNOWLEDGED -> EmeraldLive
                    IncidentState.CALL_FAILED -> RedAlert
                    else -> SlateMuted
                }

                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = incident.state.name,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = LiraBorder)

            Text(text = "PROVENANCE CHAIN:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

            TimelineEntry(
                step = "1. SENSOR TRIGGER",
                detail = "Digital trigger registered on ${incident.type} input pin",
                tagColor = CyanGlow
            )

            TimelineEntry(
                step = "2. AUTHORIZATION",
                detail = if (incident.acknowledgedBy != null) "Verified via ${incident.acknowledgedBy}" else "Awaiting RFID/App acknowledgement",
                tagColor = if (incident.acknowledgedBy != null) EmeraldLive else AmberWarning
            )

            if (incident.state in listOf(IncidentState.CALLING, IncidentState.CALL_RESULT_AVAILABLE, IncidentState.CALL_FAILED, IncidentState.ESCALATION_PENDING)) {
                TimelineEntry(
                    step = "3. CALL DISPATCH",
                    detail = "Cloud telephony escalation dispatched to supervisor",
                    tagColor = RedAlert
                )
            }

            if (incident.evidence.isNotEmpty()) {
                Text(
                    text = "Raw Evidence: ${incident.evidence.joinToString(", ")}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun TimelineEntry(step: String, detail: String, tagColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = "•", color = tagColor, fontWeight = FontWeight.Black)
        Column {
            Text(text = step, color = tagColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = detail, color = TextPrimary, fontSize = 12.sp)
        }
    }
}
