package com.cortlandwalker.ghettoxide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A lightweight redux-style ViewModel that owns immutable [state], accepts UI/System actions via
 * [postAction], and exposes one-off [effects] (navigation, toasts, etc.).
 *
 * @param S Immutable State type for the screen/feature.
 * @param A Action type (user/system intents) consumed by a [Reducer].
 * @param E One-off Effect type emitted by a [Reducer].
 *
 * ### Typical usage
 * ```kotlin
 * // Create in a Fragment/Activity (or inject with DI):
 * val vm = ViewModelProvider(this, BaseViewModel.factory(initialState, reducer))
 *   .get(BaseViewModel::class.java) as BaseViewModel<MyState, MyAction, MyEffect>
 *
 * // Send UI intents:
 * vm.postAction(MyAction.OnRefreshClicked)
 *
 * // Observe state in Compose with collectAsStateWithLifecycle, or in Android Views with repeatOnLifecycle.
 * // Collect effects (one-offs) with viewLifecycleOwner.repeatOnLifecycle(STARTED) { vm.effects.collect { ... } }
 * ```
 */
class StoreViewModel<S : Any, A : Any, E : Any>(
    /**
     * Initial immutable state for this store.
     */
    initial: S,

    /**
     * The reducer responsible for processing actions, mutating state, and emitting effects.
     */
    private val reducer: Reducer<S, A, E>
) : ViewModel() {

    // -------------------- State --------------------

    /** Backing mutable state holder. Do not expose directly. */
    private val _state = MutableStateFlow(initial)

    /**
     * Publicly exposed read-only [StateFlow] of the current state.
     * Observe in Compose with collectAsStateWithLifecycle or in Fragments with lifecycle-aware collectors.
     */
    val state: StateFlow<S> = _state.asStateFlow()

    // -------------------- Effects --------------------

    /**
     * Backing one-off effect stream. Uses a small extra buffer to avoid missing bursts.
     * Effects are expected to be short-lived (navigation, toasts, etc.).
     */
    private val _effects = MutableSharedFlow<E>(replay = 0, extraBufferCapacity = 16)

    /**
     * Publicly exposed read-only [SharedFlow] of one-off effects.
     */
    val effects: SharedFlow<E> = _effects.asSharedFlow()

    init {
        // Wire the reducer with read/write/emit hooks and the Post function.
        reducer.bind(
            read = { _state.value },
            write = { _state.value = it },
            emit = { _effects.emit(it) },
            post = ::postAction
        )
        // Give the reducer access to this VM's long-lived scope.
        reducer.attachScope(viewModelScope)
    }

    /**
     * Post an [action] to the reducer (main entrypoint for UI and internal events).
     *
     * Actions are processed serially by the reducer (see [Reducer.accept]).
     */
    fun postAction(action: A) {
        viewModelScope.launch { reducer.accept(action) }
    }

    /**
     * Convenience to dispatch actions when reducer access isn't present
     * */
    val dispatch: (A) -> Unit
        get() = reducer::postAction

    companion object {
        /**
         * Factory helper to create a [StoreViewModel].
         *
         * Use when you don't have DI. With Hilt/Koin, prefer injecting the VM and reducer directly.
         */
        fun <S : Any, A : Any, E : Any> factory(
            initial: S,
            reducer: Reducer<S, A, E>
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StoreViewModel(initial, reducer) as T
            }
        }
    }
}

/**
 * Backward-compat alias. Prefer [StoreViewModel].
 */
typealias BaseViewModel<S, A, E> = StoreViewModel<S, A, E>
