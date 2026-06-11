package com.jpillion.dailyreadingplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jpillion.dailyreadingplanner.ui.navigation.AppNavHost
import com.jpillion.dailyreadingplanner.ui.theme.DailyReadingPlannerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Proves Hilt resolves a real dependency at runtime (S2-T3 acceptance).
    @Inject
    lateinit var clock: Clock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val today = LocalDate.now(clock)
        setContent {
            DailyReadingPlannerTheme {
                AppNavHost(today = today)
            }
        }
    }
}
