package com.cortlandwalker.ghettoxide_demo.features.todo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import com.cortlandwalker.ghettoxide.FragmentReducer
import com.cortlandwalker.ghettoxide.Reducer
import com.cortlandwalker.ghettoxide.ReducerFragment

class TodoFragment : FragmentReducer<TodoState, TodoAction, TodoEffect>() {

    // Non-DI version (manual)
    override var reducer: Reducer<TodoState, TodoAction, TodoEffect> =
        TodoReducer()

    override val initialState: TodoState = TodoState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setContent {

            val state = vm.state.collectAsState().value

            TodoScreen(
                state = state,
                reducer = reducer as TodoReducer
            )
        }
    }
}