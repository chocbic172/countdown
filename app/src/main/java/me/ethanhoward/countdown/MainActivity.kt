package me.ethanhoward.countdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.ethanhoward.countdown.ui.navigation.CountdownNavHost
import me.ethanhoward.countdown.ui.theme.CountdownTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CountdownTheme {
                CountdownNavHost()
            }
        }
    }
}
