package com.cortlandwalker.ghettoxide

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Base Fragment that wires a [Reducer] into a [StoreViewModel] and
 * collects **one-off effects** with the **VIEW** lifecycle.
 *
 * Keeps feature Fragments minimal: render state and forward UI events via [reducer].
 *
 * @param S State type
 * @param A Action type
 * @param E Effect type
 * @param R Reducer type
 *
 * ### Example
 * ```kotlin
 * @AndroidEntryPoint
 * class TodoFragment : ReducerFragment<TodoState, TodoAction, TodoEffect, TodoReducer>() {
 *
 *     @Inject override lateinit var reducer: TodoReducer
 *     override val initialState = TodoState()
 *
 *     override fun onEffect(effect: TodoEffect) {
 *         // ... handle one-off effects
 *     }
 *
 *     override fun onCreateView(
 *         inflater: LayoutInflater,
 *         container: ViewGroup?,
 *         savedInstanceState: Bundle?
 *     ): View = ComposeView(requireContext()).apply {
 *         setContent {
 *             ReducerContent { state, reducer ->
 *                 TodoScreen(state = state, onAction = reducer::postAction)
 *             }
 *         }
 *     }
 * }
 * ```
 */
abstract class ReducerFragment<S : Any, A : Any, E : Any, R : Reducer<S, A, E>> : Fragment() {

    /**
     * Reducer provided by the Fragment. This reference is re-aligned to the
     * ViewModel-owned reducer instance in [onCreate] to survive config changes.
     */
    abstract var reducer: R

    /** Initial State for the screen. */
    protected abstract val initialState: S

    /**
     * Publicly exposed read-only [StateFlow] of the current state.
     * Observe in Compose with `collectAsStateWithLifecycle` or in Fragments with `lifecycleScope`.
     */
    val state: StateFlow<S>
        get() = vm.state

    /** StoreViewModel owns the "real" reducer that's bound and survives rotation. */
    protected open val vm: StoreViewModel<S, A, E> by viewModels {
        StoreViewModel.factory(
            initial = initialState,
            reducer = reducer
        )
    }

    /**
     * Handle one-off effects (navigation, snackbars, etc.). Collected with the **VIEW** lifecycle.
     */
    protected open fun onEffect(effect: E) {}

    /** Override with RESUMED when a retained offscreen Fragment remains STARTED. */
    protected open val effectMinActiveState: Lifecycle.State = Lifecycle.State.STARTED

    /**
     * Optional presentation gate for the single primary effect collector. A retained tab host
     * can supply a StateFlow that becomes false while hidden or covered by a child destination.
     * This stops collection, not reducer jobs. Non-replayed effects emitted while inactive are
     * dropped; results that must survive inactivity belong in state until acknowledged.
     */
    protected open val effectCollectionEnabled: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    /**
     * Host visibility gate for reducer start/resume/pause/stop actions. False pauses and stops
     * the presentation without destroying its view; true re-enters its current view lifecycle.
     * Supply the same host-owned flow to [effectCollectionEnabled] when effects should also pause.
     * This gate is independent so apps can choose lifecycle and effect policies separately.
     */
    protected open val reducerLifecycleEnabled: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force VM creation so its reducer is bound.
        val store = vm

        // Re-point the Fragment's reducer reference to the VM's reducer.
        // From this point on, `reducer` === the bound instance.
        @Suppress("UNCHECKED_CAST")
        reducer = store.reducer as R
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Provide view lifecycle for reducer's local collectors (e.g., DB flows tied to the view).
        reducer.attachView(viewLifecycleOwner)

        val enabled = effectCollectionEnabled
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(effectMinActiveState) {
                enabled.collectLatest { active ->
                    if (active) vm.effects.collect { effect ->
                        // Also reject an event already queued when the host closes the gate.
                        if (enabled.value) onEffect(effect)
                    }
                }
            }
        }

        val lifecycleEnabled = reducerLifecycleEnabled
        viewLifecycleOwner.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val binding = ReducerLifecycleBinding(reducer, viewLifecycleOwner.lifecycle)
            try {
                binding.attach(lifecycleEnabled.value)
                lifecycleEnabled.collect { binding.setActive(it) }
            } finally {
                binding.close()
            }
        }
    }

    override fun onDestroyView() {
        reducer.detachView()
        super.onDestroyView()
    }
}

/**
 * Backward-compat alias. Prefer [ReducerFragment].
 */
typealias FragmentReducer<S, A, E, R> = ReducerFragment<S, A, E, R>
