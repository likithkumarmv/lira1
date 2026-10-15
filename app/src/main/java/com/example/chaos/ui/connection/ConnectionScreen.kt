package com.example.chaos.ui.connection

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chaos.domain.state.LiraUiState
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.networking.BackendStatus
import com.example.chaos.networking.CellularStatus
import com.example.chaos.networking.WifiStatus
import com.example.chaos.ui.theme.*

@Composable
fun ConnectionScreen(
    viewModel: LiraViewModel,
    uiState: LiraUiState
) {
    val scrollState = rememberScrollState()
    val esp32WifiStatus by viewModel.connectionManager.esp32WifiStatus.collectAsState()
    val cellularStatus by viewModel.connectionManager.cellularStatus.collectAsState()
    val backendStatus by viewModel.connectionManager.backendStatus.collectAsState()

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
                text = "Dual-Network Routing Hub",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = "Split Network Gateway: Local ESP32 SoftAP Wi-Fi + Cellular HTTPS Cloud Routing.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Live Network Status Matrix
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "NETWORK PROTOCOL STATUS",
                    fontWeight = FontWeight.Bold,
                    color = CyanGlow,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                NetworkStatusRow(
                    label = "ESP32 Wi-Fi (Local SoftAP)",
                    value = esp32WifiStatus.name,
                    color = when (esp32WifiStatus) {
                        WifiStatus.CONNECTED -> EmeraldLive
                        WifiStatus.CONNECTING -> AmberWarning
                        WifiStatus.TIMED_OUT, WifiStatus.ERROR -> RedAlert
                        WifiStatus.DISCONNECTED -> SlateMuted
                    }
                )

                NetworkStatusRow(
                    label = "Cellular Data (Cloud Transport)",
                    value = cellularStatus.name,
                    color = when (cellularStatus) {
                        CellularStatus.AVAILABLE -> EmeraldLive
                        CellularStatus.REQUESTING -> AmberWarning
                        CellularStatus.UNAVAILABLE -> RedAlert
                    }
                )

                NetworkStatusRow(
                    label = "Telephony Calling Backend",
                    value = backendStatus.name,
                    color = when (backendStatus) {
                        BackendStatus.ONLINE -> EmeraldLive
                        BackendStatus.TESTING -> AmberWarning
                        BackendStatus.AUTH_ERROR, BackendStatus.OFFLINE -> RedAlert
                    }
                )
            }
        }

        // ESP32 Wi-Fi Settings Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "ESP32 SoftAP Parameters",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 15.sp
                )

                OutlinedTextField(
                    value = uiState.esp32Ssid,
                    onValueChange = { viewModel.updateSsid(it) },
                    label = { Text("SSID") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.esp32Pass,
                    onValueChange = { viewModel.updatePass(it) },
                    label = { Text("WPA2 Password") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.esp32BaseUrl,
                    onValueChange = { viewModel.updateEsp32Url(it) },
                    label = { Text("Local Base IP URL") },
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.connectEsp32() },
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect Wi-Fi", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.disconnectEsp32() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect", color = TextSecondary)
                    }
                }
            }
        }

        // Cloud Telephony Backend Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LiraSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LiraBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Cloud Telephony Route (Cellular)",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 15.sp
                )

                OutlinedTextField(
                    value = uiState.backendBaseUrl,
                    onValueChange = { viewModel.updateBackendUrl(it) },
                    label = { Text("Backend URL (HTTPS)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = LiraBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { viewModel.testBackend() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify Cellular Cloud Gateway", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            }
        }
    }
}

@Composable
fun NetworkStatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LiraBackground.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextPrimary, fontSize = 13.sp)
        Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
