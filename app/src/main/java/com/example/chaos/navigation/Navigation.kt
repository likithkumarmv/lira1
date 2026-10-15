package com.example.chaos.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.ui.calls.CallsScreen
import com.example.chaos.ui.connection.ConnectionScreen
import com.example.chaos.ui.dashboard.DashboardScreen
import com.example.chaos.ui.demo.DemoModeScreen
import com.example.chaos.ui.incidents.IncidentsScreen
import com.example.chaos.ui.nfc.NfcScreen
import com.example.chaos.ui.settings.SettingsScreen
import com.example.chaos.ui.theme.*

data class NavItem(val title: String, val index: Int, val icon: String)

val navigationItems = listOf(
    NavItem("Connect", 0, "📡"),
    NavItem("Dashboard", 1, "🛡️"),
    NavItem("Demo", 6, "✨"),
    NavItem("Incidents", 2, "📋"),
    NavItem("Access", 3, "🔑"),
    NavItem("Calls", 4, "📞"),
    NavItem("Policy", 5, "⚙️")
)

@Composable
fun LiraApp(viewModel: LiraViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = LiraBackground,
        bottomBar = {
            NavigationBar(
                containerColor = LiraSurface,
                contentColor = TextPrimary,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, LiraBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                navigationItems.forEach { item ->
                    val isSelected = uiState.selectedTab == item.index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(item.index) },
                        label = {
                            Text(
                                text = item.title,
                                fontSize = 10.sp,
                                color = if (isSelected) CyanNeon else SlateMuted
                            )
                        },
                        icon = {
                            Text(
                                text = item.icon,
                                fontSize = if (isSelected) 18.sp else 15.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanNeon,
                            selectedTextColor = CyanNeon,
                            indicatorColor = CyanNeon.copy(alpha = 0.15f),
                            unselectedIconColor = SlateMuted,
                            unselectedTextColor = SlateMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LiraBackground)
        ) {
            when (uiState.selectedTab) {
                0 -> ConnectionScreen(viewModel = viewModel, uiState = uiState)
                1 -> DashboardScreen(viewModel = viewModel, uiState = uiState)
                2 -> IncidentsScreen(viewModel = viewModel, uiState = uiState)
                3 -> NfcScreen(viewModel = viewModel, uiState = uiState)
                4 -> CallsScreen(viewModel = viewModel, uiState = uiState)
                5 -> SettingsScreen(viewModel = viewModel, uiState = uiState)
                6 -> DemoModeScreen(viewModel = viewModel, uiState = uiState)
                else -> DashboardScreen(viewModel = viewModel, uiState = uiState)
            }
        }
    }
}
