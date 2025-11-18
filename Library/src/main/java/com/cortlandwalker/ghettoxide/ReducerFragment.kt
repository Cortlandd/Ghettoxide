package com.cortlandwalker.ghettoxide

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Base Fragment that wires a [Reducer] into a [BaseViewModel] and
 * collects **one-off effects** with the **VIEW** lifecycle.
 *
 * Keeps feature Fragments minimal: render state and forward UI events via [vm.postAction].
 *
 * @param S State type
 * @param A Action type
 * @param E Effect type
 *
 * ### Example
 * ```kotlin
 * class WorkoutListFragment : ReducerFragment<WorkoutListState, WorkoutListAction, WorkoutListEffect>() {
 *   @Inject override lateinit var reducer: WorkoutListReducer
 *
 *   override val initialState = WorkoutListState()
 *
 *   OR
 *
 *   private val args by navArgs<UpsertWorkoutFragmentArgs>()
 *
 *   override val initialState: UpsertWorkoutState by lazy {
 *      UpsertWorkoutState(workoutId = args.workoutId)
 *   }
 *
 *   override fun onEffect(effect: WorkoutListEffect) {
 *     when (effect) {
 *       is WorkoutListEffect.NavigateToAdd ->
 *         findNavController().navigate(R.id.action_list_to_add)
 *       is WorkoutListEffect.ShowMessage ->
 *         Snackbar.make(requireView(), effect.text, Snackbar.LENGTH_LONG).show()
 *     }
 *   }
 *
 *   // In onViewCreated or Compose, you typically:
 *   // vm.state ... observe to render
 *   // vm.postAction(WorkoutListAction.Load) ... but this base calls reducer.onLoadAction() automatically.
 * }
 * ```
 */
abstract class ReducerFragment<S : Any, A : Any, E : Any> : Fragment() {

    /**
     * The reducer instance that will process actions and manage state/effects.
     * Provide via DI or by constructing with repositories/use-cases.
     */
    protected abstract val reducer: Reducer<S, A, E>

    /**
     * Initial State for the screen.
     */
    protected abstract val initialState: S

    /**
     * The screen's Store-style ViewModel. Exposed as protected for UI binding.
     */
    protected lateinit var vm: StoreViewModel<S, A, E>
        private set

    /**
     * Handle one-off effects (navigation, snackbars, etc.). Collected with the **VIEW** lifecycle.
     *
     * ### Example
     * ```kotlin
     * override fun onEffect(effect: WorkoutListEffect) {
     *   when (effect) {
     *     is WorkoutListEffect.NavigateToEdit -> findNavController().navigate(
     *       ListFragmentDirections.actionListToEdit(effect.id)
     *     )
     *     is WorkoutListEffect.ShowToast -> Toast.makeText(requireContext(), effect.message, LENGTH_SHORT).show()
     *   }
     * }
     * ```
     */
    protected open fun onEffect(effect: E) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("UNCHECKED_CAST")
        vm = ViewModelProvider(
            this,
            StoreViewModel.factory(initialState, reducer)
        )[StoreViewModel::class.java] as StoreViewModel<S, A, E>
        // The ViewModel binds the reducer and attaches viewModelScope internally.
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
typealias FragmentReducer<S, A, E> = ReducerFragment<S, A, E>
