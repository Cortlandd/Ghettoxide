package com.cortlandwalker.ghettoxide_demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.cortlandwalker.ghettoxide.StoreViewModel
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoAction
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoEffect
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoReducer
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoScreen
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoState
import com.cortlandwalker.ghettoxide_demo.ui.theme.GhettoxideTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
    }
}
