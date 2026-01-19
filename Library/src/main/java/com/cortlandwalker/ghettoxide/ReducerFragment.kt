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
import kotlinx.coroutines.flow.StateFlow
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

        // Collect effects when the view is at least STARTED.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.effects.collect(::onEffect)
            }
        }

        // Optionally trigger an initial Action when the view is created.
        reducer.onLoadAction()?.let(vm::postAction)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Let the reducer clean up any additional jobs/resources (viewStore collectors are lifecycle-aware).
        reducer.onCleared()
    }
}

/**
 * Backward-compat alias. Prefer [ReducerFragment].
 */
typealias FragmentReducer<S, A, E, R> = ReducerFragment<S, A, E, R>
