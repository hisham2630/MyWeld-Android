package com.myweld.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myweld.app.data.repository.DeviceRepository
import com.myweld.app.navigation.NavGraph
import com.myweld.app.ui.theme.MyWeldTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val deviceRepository: DeviceRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val isDarkMode by deviceRepository.isDarkMode.collectAsStateWithLifecycle(
                initialValue = true,
            )

            MyWeldTheme(darkTheme = isDarkMode) {
                NavGraph()
            }
        }
    }
}
