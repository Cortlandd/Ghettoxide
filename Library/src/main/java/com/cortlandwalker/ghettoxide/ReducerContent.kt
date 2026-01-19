package com.cortlandwalker.ghettoxide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * A Composable that simplifies connecting a [ReducerFragment] to a Composable screen.
 * It collects the state from the ViewModel and provides it to its content.
 */
@Composable
fun <S : Any, A : Any, E : Any, R : Reducer<S, A, E>> ReducerFragment<S, A, E, R>.ReducerContent(
    content: @Composable (state: S, reducer: R) -> Unit
) {
    val stateValue = state.collectAsStateWithLifecycle().value
    content(stateValue, reducer)
}
