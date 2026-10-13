package com.cortlandwalker.ghettoxide

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle

/**
 * A Composable that simplifies connecting a [ReducerFragment] to a Composable screen.
 * It collects the state from the ViewModel and provides it to its content.
 */
@Composable
fun <S : Any, A : Any, E : Any, R : Reducer<S, A, E>> ReducerFragment<S, A, E, R>.ReducerContent(
    content: @Composable (state: S, reducer: R) -> Unit
) {
    ReducerContent(collectWhen = true, content = content)
}

/**
 * Opt-in state collection policy for a retained Fragment view. Inactivity keeps the last rendered
 * snapshot; reactivation collects the latest store state. Effect collection is configured separately
 * through ReducerFragment.effectCollectionEnabled and effectMinActiveState.
 */
@Composable
fun <S : Any, A : Any, E : Any, R : Reducer<S, A, E>> ReducerFragment<S, A, E, R>.ReducerContent(
    collectWhen: Boolean,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    content: @Composable (state: S, reducer: R) -> Unit
) {
    val stateValue = state.collectStateWhen(collectWhen, minActiveState).value
    content(stateValue, reducer)
}
