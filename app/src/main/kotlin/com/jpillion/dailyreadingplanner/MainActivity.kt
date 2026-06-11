package com.jpillion.dailyreadingplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jpillion.dailyreadingplanner.ui.navigation.AppNavHost
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailyReadingPlannerTheme {
                AppNavHost()
            }
        }
    }
}
