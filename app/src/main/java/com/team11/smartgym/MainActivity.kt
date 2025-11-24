@file:OptIn(ExperimentalMaterial3Api::class)

package com.team11.smartgym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.team11.smartgym.ui.dashboard.DashboardScreen
import com.team11.smartgym.ui.history.HistoryScreen
import com.team11.smartgym.ui.profile.ProfileScreen
import com.team11.smartgym.ui.theme.SmartGymTheme
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.smartgym.auth.AuthViewModel
import com.team11.smartgym.ui.auth.AuthScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartGymAppRoot()
        }
    }
}

@Composable
fun SmartGymAppRoot() {
    SmartGymTheme {
        val authViewModel: AuthViewModel = viewModel()
        val authState by authViewModel.uiState.collectAsState()

        if (!authState.isLoggedIn) {
            // Show login / signup flow
            AuthScreen(authViewModel = authViewModel)
        } else {
            // Show your existing main tabs
            var selectedTab by remember { mutableStateOf(0) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "SmartGym",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Filled.Favorite, contentDescription = "Dashboard") },
                            label = { Text("Dashboard") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                            label = { Text("History") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                            label = { Text("Profile") }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> DashboardScreen()
                        1 -> HistoryScreen()
                        2 -> ProfileScreen(userProfile = authState.currentUser)
                    }
                }
            }
        }
    }
}
