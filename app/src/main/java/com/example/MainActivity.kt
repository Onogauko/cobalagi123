package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.SalesViewModel
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: SalesViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        // 1. Core Home Dashboard Screen
                        composable("dashboard") {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { sku ->
                                    navController.navigate("detail/$sku")
                                },
                                onNavigateToAnalytics = {
                                    navController.navigate("analytics")
                                }
                            )
                        }

                        // 2. Ranking Analytics Dashboard Screen
                        composable("analytics") {
                            AnalyticsScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToDetail = { sku ->
                                    navController.navigate("detail/$sku")
                                }
                            )
                        }

                        // 3. Granular SKU Explorer Screen
                        composable(
                            route = "detail/{sku}",
                            arguments = listOf(navArgument("sku") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val sku = backStackEntry.arguments?.getString("sku") ?: ""
                            DetailScreen(
                                sku = sku,
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
