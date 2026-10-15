package com.example.chaos.ui.nfc

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chaos.domain.state.LiraUiState
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.ui.theme.*

@Composable
fun NfcScreen(
    viewModel: LiraViewModel,
    uiState: LiraUiState
) {
    val scrollState = rememberScrollState()
    var newTagUid by remember { mutableStateOf("") }
    var newTagLabel by remember { mutableStateOf("") }

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
                text = "Authorization & Tag Manager",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = "Hardware RC522 Reader on shared SPI + Native Android Phone NFC Engine.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Mode A: RC522 Reader
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MODE A: RC522 SPI READER",
                        fontWeight = FontWeight.Bold,
                        color = CyanGlow,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )

                    val ready = uiState.deviceStatus.sensors["rc522"]?.ready == true
                    Box(
                        modifier = Modifier
                            .background(if (ready) EmeraldLive.copy(alpha = 0.15f) else SlateMuted.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, if (ready) EmeraldLive else SlateMuted, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (ready) "READY" else "OFFLINE",
                            color = if (ready) EmeraldLive else SlateMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "Hardware SPI: SCK (GPIO 18), MOSI (GPIO 23), MISO (GPIO 19), CS (GPIO 21), RST (GPIO 22). Tapping an enrolled tag toggles arm/disarm or acknowledges pending alerts.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                HorizontalDivider(color = LiraBorder)

                Text(
                    text = "DEMO TAG ALLOWLIST (${uiState.registeredTags.size} ENROLLED)",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                )

                uiState.registeredTags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LiraSurfaceElevated.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = tag.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "UID: ${maskUid(tag.uid)} • ${tag.enrolledAt}", color = TextSecondary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { viewModel.removeTag(tag.uid) }) {
                            Text(text = "✕", color = RedAlert, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Enroll inputs
                OutlinedTextField(
                    value = newTagUid,
                    onValueChange = { newTagUid = it },
                    label = { Text("Tag UID (Hex, e.g. 4A:5B:6C:7D)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newTagLabel,
                    onValueChange = { newTagLabel = it },
                    label = { Text("Label (e.g. Master Keycard)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.enrollTag(newTagUid, newTagLabel)
                        newTagUid = ""
                        newTagLabel = ""
                    },
                    enabled = newTagUid.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enroll Tag in Allowlist", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Mode B: Phone NFC
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
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
                        text = "MODE B: PHONE NFC READER",
                        fontWeight = FontWeight.Bold,
                        color = CyanGlow,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )

                    if (uiState.isPhoneNfcSupported) {
                        Switch(
                            checked = uiState.isPhoneNfcEnabled,
                            onCheckedChange = { viewModel.togglePhoneNfc(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyanNeon,
                                checkedTrackColor = SkyBlue
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .background(SlateMuted.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("NOT SUPPORTED", color = SlateMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!uiState.isPhoneNfcSupported) {
                    Text(
                        text = "This phone lacks NFC hardware. Full functionality remains supported through the RC522 module on the ESP32.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                } else if (uiState.isPhoneNfcEnabled) {
                    Text(
                        text = "Phone NFC is ACTIVE. Tap an authorized NFC keycard or token to the back of this phone to toggle Arm/Disarm or acknowledge.",
                        fontSize = 12.sp,
                        color = EmeraldLive
                    )
                    if (uiState.lastScannedPhoneTagUid != null) {
                        Text(
                            text = "Last Scanned UID: ${maskUid(uiState.lastScannedPhoneTagUid)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                    }
                } else {
                    Text(
                        text = "Phone NFC is available. Toggle ON to enable reader mode.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

fun maskUid(uid: String): String {
    if (uid.length < 5) return "***"
    return uid.take(2) + ":**:**:" + uid.takeLast(2)
}
