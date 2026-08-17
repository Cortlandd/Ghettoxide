package com.cortlandwalker.ghettoxide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow

/**
 * Compose helpers for reducer-backed [StoreViewModel] instances.
 *
 * These helpers keep reducer screens concise while still following lifecycle-safe collection
 * patterns.
 *
 * Example:
 * ```kotlin
 * val (store, reducer) = rememberStoreViewModel(
 *     key = "search",
 *     initialState = SearchState(),
 *     reducerFactory = { SearchReducer(repository) }
 * )
 *
 * StoreContent(store = store) { state, dispatch ->
 *     SearchScreen(
 *         state = state,
 *         onAction = dispatch
 *     )
 * }
 * ```
 *
 * @author Cortland Walker
 */

/**
 * Collects [StoreViewModel.state] with `collectAsStateWithLifecycle` and forwards a typed
 * dispatch function to the provided [content].
 *
 * This is the quickest way to render a reducer-backed screen in Compose when you do not need
 * direct access to the store object inside the UI.
 *
 * Example:
 * ```kotlin
 * StoreContent(store = store) { state, dispatch ->
 *     InboxScreen(
 *         state = state,
 *         onAction = dispatch
 *     )
 * }
 * ```
 *
 * @author Cortland Walker
 */
@Composable
fun <S : Any, A : Any, E : Any> StoreContent(
    store: StoreViewModel<S, A, E>,
    content: @Composable (state: S, dispatch: (A) -> Unit) -> Unit
) {
    val stateValue by store.state.collectAsStateWithLifecycle()
    content(stateValue, store.dispatch)
}

/**
 * Observes one-off reducer effects from Compose using [repeatOnLifecycle].
 *
 * This prevents duplicate or offscreen effect collection when the UI is stopped and restarted.
 * The latest [onEffect] lambda is used without restarting the collector, so an old navigation or
 * screen callback is not retained by a long-lived coroutine.
 *
 * Example:
 * ```kotlin
 * ObserveEffects(store.effects) { effect ->
 *     when (effect) {
 *         is FeedEffect.ScrollToTop -> listState.animateScrollToItem(0)
 *         is FeedEffect.OpenSettings -> onOpenSettings()
 *     }
 * }
 * ```
 *
 * @author Cortland Walker
 */
@Composable
fun <E : Any> ObserveEffects(
    effects: Flow<E>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (E) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner, minActiveState) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            effects.collect { currentOnEffect(it) }
        }
    }
}

/**
 * Creates or retrieves a keyed [StoreViewModel] from Compose and returns it alongside its reducer.
 *
 * This is especially useful for:
 *
 * - tab child stores
 * - nested feature stores
 * - local reducer-driven surfaces that do not warrant a dedicated injected ViewModel class
 *
 * The [key] should remain stable for the lifetime of the logical feature instance. Reducer
 * creation is also scoped to [viewModelStoreOwner], preventing one reducer from being rebound to
 * stores owned by different navigation entries or Activities.
 *
 * Example:
 * ```kotlin
 * val (store, reducer) = rememberStoreViewModel(
 *     key = "tab:profile/uploads",
 *     initialState = UploadsState(),
 *     reducerFactory = { UploadsReducer(repository) }
 * )
 * ```
 *
 * @author Cortland Walker
 */
@Composable
fun <S : Any, A : Any, E : Any, R : Reducer<S, A, E>> rememberStoreViewModel(
    key: String,
    initialState: S,
    reducerFactory: () -> R,
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
): Pair<StoreViewModel<S, A, E>, R> {
    val reducer = remember(key, viewModelStoreOwner) { reducerFactory() }
    val factory = remember(key, initialState, reducer) {
        StoreViewModel.factory(
            initial = initialState,
            reducer = reducer
        )
    }
    val store: StoreViewModel<S, A, E> = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        key = key,
        factory = factory
    )
    @Suppress("UNCHECKED_CAST")
    return store to (store.reducer as R)
}
