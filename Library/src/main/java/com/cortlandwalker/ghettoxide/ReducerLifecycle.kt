package com.cortlandwalker.ghettoxide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Connects a Compose-owned reducer to start/resume/pause/stop action hooks. Keep it composed for
 * the presentation's lifetime and pass false for [isActive] while retained but hidden or covered.
 * Returning to an already-resumed owner dispatches start and resume again, allowing refresh on
 * every appearance. Ordinary recomposition does not dispatch lifecycle actions.
 *
 * Install exactly one binding per presentation. [ReducerFragment] and [reducerTabSpec] already
 * install it; their content must not add another. This does not attach view-local collectors,
 * change state/effect subscriptions, cancel jobs, or rerun the store's one-time load automatically.
 */
@Composable
fun <S : Any, A : Any, E : Any> BindReducerLifecycle(
    reducer: Reducer<S, A, E>,
    isActive: Boolean = true,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val binding = remember(reducer, lifecycle) { ReducerLifecycleBinding(reducer, lifecycle) }
    DisposableEffect(binding) {
        binding.attach(isActive)
        onDispose { binding.close() }
    }
    SideEffect { binding.setActive(isActive) }
}

/** UI-owned binding shared by Fragment and Compose hosts; never retained by the reducer/store. */
internal class ReducerLifecycleBinding<S : Any, A : Any, E : Any>(
    private val reducer: Reducer<S, A, E>,
    private val lifecycle: Lifecycle,
) : AutoCloseable {
    private var attached = false
    private var active = false
    private var started = false
    private var resumed = false
    private val observer = LifecycleEventObserver { _, _ -> update() }

    fun attach(isActive: Boolean) {
        check(!attached) { "Reducer lifecycle is already attached." }
        active = isActive
        attached = true
        lifecycle.addObserver(observer)
        update()
    }

    fun setActive(isActive: Boolean) {
        active = isActive
        update()
    }

    override fun close() {
        if (!attached) return
        attached = false
        lifecycle.removeObserver(observer)
        update()
    }

    private fun isAtLeast(state: Lifecycle.State) =
        attached && active && lifecycle.currentState.isAtLeast(state)

    private fun update() {
        // Change flags before posting actions, since Main.immediate dispatch can be reentrant.
        if (resumed && !isAtLeast(Lifecycle.State.RESUMED)) {
            resumed = false
            reducer.onPauseAction()?.let(reducer::postAction)
        }
        if (started && !isAtLeast(Lifecycle.State.STARTED)) {
            started = false
            reducer.onStopAction()?.let(reducer::postAction)
        }
        if (!started && isAtLeast(Lifecycle.State.STARTED)) {
            started = true
            reducer.onStartAction()?.let(reducer::postAction)
        }
        if (!resumed && isAtLeast(Lifecycle.State.RESUMED)) {
            resumed = true
            reducer.onResumeAction()?.let(reducer::postAction)
        }
    }
}
