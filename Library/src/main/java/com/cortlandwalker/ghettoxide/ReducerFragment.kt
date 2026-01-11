package com.cortlandwalker.ghettoxide

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Base Fragment that wires a [Reducer] into a [StoreViewModel] and
 * collects **one-off effects** with the **VIEW** lifecycle.
 *
 * Keeps feature Fragments minimal: render state and forward UI events via [vm.postAction].
 *
 * @param S State type
 * @param A Action type
 * @param E Effect type
 * @param R Reducer type
 *
 * ### Example
 * ```kotlin
 * // DI (Hilt) example
 * @AndroidEntryPoint
 * class TodoFragment :
 *     ReducerFragment<TodoState, TodoAction, TodoEffect, TodoReducer>() {
 *
 *     @Inject
 *     override lateinit var reducer: TodoReducer
 *
 *     override val initialState = TodoState()
 *
 *     override fun onEffect(effect: TodoEffect) {
 *         when (effect) {
 *             is TodoEffect.ToastTodo ->
 *                 Toast.makeText(requireContext(), "Saved: ${effect.todoName}", Toast.LENGTH_SHORT).show()
 *         }
 *     }
 *
 *     override fun onCreateView(
 *         inflater: LayoutInflater,
 *         container: ViewGroup?,
 *         savedInstanceState: Bundle?
 *     ): View = ComposeView(requireContext()).apply {
 *         setContent {
 *             val state = vm.state.collectAsState().value
 *             TodoScreen(
 *                 state = state,
 *                 reducer = reducer // or onAction = reducer::postAction
 *             )
 *         }
 *     }
 * }
 *
 * // Non-DI example
 * class TodoFragment :
 *     ReducerFragment<TodoState, TodoAction, TodoEffect, TodoReducer>() {
 *
 *     override var reducer: TodoReducer = TodoReducer()
 *
 *     override val initialState = TodoState()
 *
 *     override fun onCreateView(
 *         inflater: LayoutInflater,
 *         container: ViewGroup?,
 *         savedInstanceState: Bundle?
 *     ): View {
 *         val binding = FragmentTodoBinding.inflate(inflater, container, false)
 *
 *         // XML example – send actions via reducer.postAction(...)
 *         binding.addButton.setOnClickListener {
 *             reducer.postAction(TodoAction.TappedTodo("New todo"))
 *         }
 *
 *         binding.saveButton.setOnClickListener {
 *             val name = binding.todoInput.text.toString()
 *             reducer.postAction(TodoAction.Save(name))
 *         }
 *
 *         // Observe state with a simple lifecycleScope if not using Compose:
 *         viewLifecycleOwner.lifecycleScope.launch {
 *             viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
 *                 vm.state.collect { state ->
 *                     // render state.items into a RecyclerView, etc.
 *                 }
 *             }
 *         }
 *
 *         return binding.root
 *     }
 * }
 * ```
 */
abstract class ReducerFragment<S : Any, A : Any, E : Any, R : Reducer<S, A, E>> : Fragment() {

    /**
     * Reducer provided by the Fragment.
     *
     * - Non-DI:
     *   override var reducer: TodoReducer = TodoReducer()
     *
     * - DI (Hilt):
     *   @Inject override lateinit var reducer: TodoReducer
     *
     * This reference is re-aligned to the ViewModel-owned reducer instance
     * in [onCreate], so that `reducer` always points at the bound instance
     * across configuration changes.
     */
    abstract var reducer: R

    /**
     * Initial State for the screen.
     */
    protected abstract val initialState: S

    /**
     * StoreViewModel owns the "real" reducer that's bound and survives rotation.
     */
    protected open val vm: StoreViewModel<S, A, E> by viewModels {
        StoreViewModel.factory(
            initial = initialState,
            reducer = reducer       // uses whatever the Fragment has *now*
        )
    }

    /**
     * Handle one-off effects (navigation, snackbars, etc.). Collected with the **VIEW** lifecycle.
     *
     * ### Example
     * ```kotlin
     * override fun onEffect(effect: TodoEffect) {
     *     when (effect) {
     *         is TodoEffect.ToastTodo ->
     *             Toast.makeText(requireContext(), effect.todoName, Toast.LENGTH_SHORT).show()
     *     }
     * }
     * ```
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
