package com.cortlandwalker.tabshowcase

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.cortlandwalker.tabshowcase.ui.theme.GhettoxideTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GhettoxideTheme {
                AppNavigation()
            }
        }
    }
}
