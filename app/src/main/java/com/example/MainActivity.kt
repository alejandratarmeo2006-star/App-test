package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.StorageScreen
import com.example.ui.StorageViewModel
import com.example.ui.theme.StorageLensTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StorageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StorageLensTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StorageScreen(viewModel = viewModel)
                }
            }
        }
    }
}
