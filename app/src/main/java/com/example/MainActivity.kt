package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.PmAiApp
import com.example.viewmodel.PmAiViewModel

class MainActivity : ComponentActivity() {

    private var pmAiViewModelRef: PmAiViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge for Android 16
        enableEdgeToEdge()

        // STEP 3: Render Compose UI
        setContent {
            val vm: PmAiViewModel = viewModel()
            pmAiViewModelRef = vm
            PmAiApp(viewModel = vm)
        }
    }

    override fun onResume() {
        super.onResume()
        pmAiViewModelRef?.refreshSystemStates()
    }
}
