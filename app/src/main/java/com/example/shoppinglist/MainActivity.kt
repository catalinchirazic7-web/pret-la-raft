package com.example.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.shoppinglist.ui.AppContent
import com.example.shoppinglist.ui.MainViewModel
import com.example.shoppinglist.ui.branding.BrandingSplash
import com.example.shoppinglist.ui.theme.ShoppingListTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.factory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash-ul de sistem nu este ținut; brandingul Compose preia imediat după el.
        installSplashScreen().setKeepOnScreenCondition { false }
        setTheme(R.style.Theme_ShoppingList)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isAppReady by mainViewModel.isAppReady.collectAsState()
            ShoppingListTheme {
                Crossfade(targetState = isAppReady, label = "startup") { ready ->
                    if (ready) AppContent() else BrandingSplash()
                }
            }
        }
    }
}
