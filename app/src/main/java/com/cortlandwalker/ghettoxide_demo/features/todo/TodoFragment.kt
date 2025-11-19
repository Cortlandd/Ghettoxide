package com.cortlandwalker.ghettoxide_demo.features.todo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.cortlandwalker.ghettoxide.Reducer
import com.cortlandwalker.ghettoxide.ReducerFragment

// Unused for now but typically nav_graph and this fragment is used

class TodoFragment(
    override val reducer: Reducer<TodoState, TodoAction, TodoEffect>,
    override val initialState: TodoState
) : ReducerFragment<TodoState, TodoAction, TodoEffect>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                //TodoScreen(state, reducer)
            }
        }
    }

}