package com.cortlandwalker.ghettoxide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.State
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

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
    StoreContent(store, collectWhen = true, content = content)
}

/**
 * Collect state only while [collectWhen] and [minActiveState] permit it. The composition remains
 * mounted with its last snapshot while inactive and catches up on reactivation. Reducer work is
 * unaffected. The overload without these options preserves the normal STARTED behavior.
 */
@Composable
fun <S : Any, A : Any, E : Any> StoreContent(
    store: StoreViewModel<S, A, E>,
    collectWhen: Boolean,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    content: @Composable (state: S, dispatch: (A) -> Unit) -> Unit
) {
    val stateValue by store.state.collectStateWhen(collectWhen, minActiveState)
    content(stateValue, store.dispatch)
}

@Composable
internal fun <S> StateFlow<S>.collectStateWhen(
    collectWhen: Boolean,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): State<S> = key(this) {
    // Keep one snapshot across gate changes, but never carry it into a different store owner.
    val source = remember(this, collectWhen) { if (collectWhen) this else emptyFlow() }
    source.collectAsStateWithLifecycle(initialValue = value, minActiveState = minActiveState)
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
    ObserveEffects(effects, collectWhen = true, minActiveState = minActiveState, onEffect = onEffect)
}

/**
 * Opt-in visibility gate for an effect collector. This does not add buffering or replay: durable
 * results must remain in state until the active UI acknowledges them. Other collectors of the same
 * flow are independent and must use their own appropriate gate.
 */
@Composable
fun <E : Any> ObserveEffects(
    effects: Flow<E>,
    collectWhen: Boolean,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (E) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)
    val currentEnabled by rememberUpdatedState(collectWhen)
    LaunchedEffect(effects, lifecycleOwner, minActiveState, collectWhen) {
        if (collectWhen) lifecycleOwner.repeatOnLifecycle(minActiveState) {
            effects.collect { if (currentEnabled) currentOnEffect(it) }
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
