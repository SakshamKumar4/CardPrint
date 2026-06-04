package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LayoutScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Supports full-edge transparent navigation bar & status bars natively
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val screen by viewModel.currentScreen.collectAsState()
                val projects by viewModel.allProjects.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val activeScreen = screen) {
                        is Screen.Home -> {
                            HomeScreen(
                                viewModel = viewModel,
                                projects = projects,
                                onProjectClicked = { project ->
                                    viewModel.navigateTo(Screen.LayoutPreview(project))
                                }
                            )
                        }
                        is Screen.Editor -> {
                            EditorScreen(
                                viewModel = viewModel,
                                project = activeScreen.project,
                                imageIndex = activeScreen.imageIndex
                            )
                        }
                        is Screen.LayoutPreview -> {
                            LayoutScreen(
                                viewModel = viewModel,
                                project = activeScreen.project
                            )
                        }
                    }
                }
            }
        }
    }
}
