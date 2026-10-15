package com.example.chaos.ui.demo

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.chaos.domain.models.DemoEvent
import com.example.chaos.domain.models.DemoSession
import com.example.chaos.domain.state.LiraUiState
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.ui.theme.*

@Composable
fun DemoModeScreen(
    viewModel: LiraViewModel,
    uiState: LiraUiState
) {
    val session = uiState.demoSession ?: DemoSession(
        sessionId = "demo-init",
        startTime = "",
        startTimeIst = "Initializing..."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LiraBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Prominent Simulation Banner
        SimulationBanner(session = session)

        // 2. Metrics 4-Grid
        DemoMetricsGrid(session = session)

        // 3. Interactive Controls Row
        DemoControlsBar(
            viewModel = viewModel,
            uiState = uiState,
            session = session
        )

        // 4. Voice Call Active Card (if call exists)
        if (session.calls.isNotEmpty()) {
            val call = session.calls.first()
            DemoCallCard(call = call)
        }

        // 5. Timeline Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SIMULATED ACCESS TIMELINE",
                color = CyanNeon,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "${session.events.size} events logged",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // 6. Chronological Event List
        if (session.events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LiraSurface)
                    .border(1.dp, LiraBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap 'Run Showcase' or 'Next Step' to simulate people entry.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(session.events) { event ->
                    DemoEventCard(event = event) {
                        viewModel.selectDemoEvent(event)
                    }
                }
            }
        }
    }

    // Event Details Modal
    if (uiState.selectedDemoEvent != null) {
        DemoEventDetailDialog(
            event = uiState.selectedDemoEvent,
            onDismiss = { viewModel.selectDemoEvent(null) }
        )
    }

    // End-of-Session Report Modal
    if (uiState.showEndDemoReportDialog) {
        DemoEndReportDialog(
            viewModel = viewModel,
            session = session,
            emailSent = uiState.demoReportSentSuccess,
            onDismiss = { viewModel.dismissDemoReportDialog() }
        )
    }
}

@Composable
fun SimulationBanner(session: DemoSession) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(AmberWarning, Color(0xFFF97316)))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (session.status == "ACTIVE") EmeraldLive else AmberWarning)
                    )
                    Text(
                        text = "SIMULATION MODE",
                        color = AmberWarning,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "• ${session.status}",
                        color = if (session.status == "ACTIVE") EmeraldLive else AmberWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "Session: ${session.sessionId} | Started: ${session.startTimeIst}",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${session.durationSeconds}s Active",
                    color = CyanNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun DemoMetricsGrid(session: DemoSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            title = "TOTAL",
            value = "${session.totalEntries}",
            color = CyanNeon,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "AUTHORIZED",
            value = "${session.authorizedCount}",
            color = EmeraldLive,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "UNAUTHORIZED",
            value = "${session.unauthorizedCount}",
            color = RedAlert,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "INSIDE",
            value = "${session.peopleInsideCount}",
            color = Color(0xFFFBBF24),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = LiraSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LiraBorder)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(text = title, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DemoControlsBar(
    viewModel: LiraViewModel,
    uiState: LiraUiState,
    session: DemoSession
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.runShowcaseScenario() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isShowcaseRunning) AmberWarning else CyanGlow
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                Text(
                    text = if (uiState.isShowcaseRunning) "⏸ Pause Showcase" else "▶ Run Showcase",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = { viewModel.triggerNextShowcaseStep() },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Step +1", color = TextPrimary, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { viewModel.resetDemoMode() },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(0.9f)
            ) {
                Text("Reset", color = TextSecondary, fontSize = 12.sp)
            }
        }

        // Prominent "I'm Back — End Demo"
        Button(
            onClick = { viewModel.endDemoMode() },
            colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🏁 I'm Back — End Demo & Email Report",
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun DemoEventCard(
    event: DemoEvent,
    onClick: () -> Unit
) {
    val isAuth = event.authResult == "Authorized"
    val isExit = event.type == "EXIT"
    val statusColor = if (!isAuth) RedAlert else (if (isExit) Color(0xFFA855F7) else EmeraldLive)

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = LiraSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.5f))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = event.personLabel,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isExit) "EXIT" else event.authResult.uppercase(),
                            color = statusColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp
                        )
                    }
                }
                Text(
                    text = event.explanation,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = event.timestampIst.split(",").lastOrNull()?.trim() ?: event.timestampIst,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (event.responseStatus == "ESCALATION_TRIGGERED") "⚡ CALL TRIGGERED" else "✔ RESOLVED",
                    color = if (event.responseStatus == "ESCALATION_TRIGGERED") AmberWarning else EmeraldLive,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DemoCallCard(call: com.example.chaos.domain.models.CallEscalation) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2537)),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanNeon)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📞 AI VOICE TELEPHONY (BOLNA)",
                    color = CyanNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EmeraldLive.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = call.status.uppercase(),
                        color = EmeraldLive,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                text = "Recipient: ${call.contactName} (${call.contactPhoneMasked})",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (!call.transcript.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LiraBackground)
                        .padding(8.dp)
                ) {
                    Text(
                        text = call.transcript,
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun DemoEventDetailDialog(
    event: DemoEvent,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Event Provenance Details",
                    fontWeight = FontWeight.Bold,
                    color = CyanNeon,
                    fontSize = 16.sp
                )

                DetailRow("Event ID", event.eventId)
                DetailRow("Person", event.personLabel)
                DetailRow("Type", event.type)
                DetailRow("Authorization", event.authResult)
                DetailRow("Time (IST)", event.timestampIst)
                DetailRow("Status", event.responseStatus)

                Text(
                    text = "Explanation:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.explanation,
                    color = TextPrimary,
                    fontSize = 12.sp
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DemoEndReportDialog(
    viewModel: LiraViewModel,
    session: DemoSession,
    emailSent: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanNeon)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session Summary Report",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 17.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (emailSent) EmeraldLive.copy(alpha = 0.2f) else RedAlert.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (emailSent) "EMAIL DELIVERED" else "EMAIL PENDING",
                            color = if (emailSent) EmeraldLive else RedAlert,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Report emailed to: ${session.reportRecipient}",
                    color = CyanNeon,
                    fontSize = 12.sp
                )

                DetailRow("Duration", "${session.durationSeconds}s")
                DetailRow("Total Entries", "${session.totalEntries}")
                DetailRow("Authorized", "${session.authorizedCount}")
                DetailRow("Unauthorized", "${session.unauthorizedCount}")
                DetailRow("Still Inside", "${session.peopleInsideCount}")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resendDemoReport() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Resend Email", fontSize = 11.sp, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = { viewModel.exportDemo("csv") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export CSV", fontSize = 11.sp, color = TextPrimary)
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
