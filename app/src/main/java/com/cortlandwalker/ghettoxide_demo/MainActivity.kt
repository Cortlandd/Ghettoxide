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
import androidx.lifecycle.ViewModelProvider
import com.cortlandwalker.ghettoxide.StoreViewModel
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoAction
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoEffect
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoReducer
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoScreen
import com.cortlandwalker.ghettoxide_demo.features.todo.TodoState
import com.cortlandwalker.ghettoxide_demo.ui.theme.GhettoxideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create StoreViewModel using Ghettoxide's factory
        val store = ViewModelProvider(
            this,
            StoreViewModel.factory(
                initial = TodoState(),
                reducer = TodoReducer()
            )
        )[StoreViewModel::class.java] as StoreViewModel<TodoState, TodoAction, TodoEffect>

        setContent {
            GhettoxideTheme {
                val state by store.state.collectAsState()

                // observe effects
                LaunchedEffect(Unit) {
                    store.effects.collect { eff ->
                        when (eff) {
                            is TodoEffect.ToastTodo ->
                                Toast.makeText(this@MainActivity, eff.todoName, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                TodoScreen(
                    state = state,
                    dispatch = store::postAction
                )
            }
        }
    }
}
