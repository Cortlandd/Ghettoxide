package com.cortlandwalker.ghettoxide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.flow.Flow

/**
 * Tab primitives and Compose host helpers for reducer-driven tab navigation.
 *
 * These APIs are intentionally explicit:
 *
 * - a tab is identified by a stable [TabId]
 * - selection/backstack state lives in [TabHostState]
 * - reducer-backed child screens opt into visibility callbacks through [TabChildReducer]
 * - Compose renders the active tab through [TabHost]
 *
 * Example:
 * ```kotlin
 * enum class AppTab(
 *     override val stableKey: String,
 *     val label: String
 * ) : TabId {
 *     Home("home", "Home"),
 *     Search("search", "Search")
 * }
 *
 * val controller = rememberTabHostController(
 *     key = "app-tabs",
 *     initialTabId = AppTab.Home
 * )
 *
 * val tabs = listOf(
 *     homeTabSpec(onNavigate),
 *     searchTabSpec(onNavigate)
 * )
 *
 * TabHost(
 *     controller = controller,
 *     tabs = tabs
 * )
 * ```
 *
 * @author Cortland Walker
 */

/**
 * Stable identifier contract for a tab within a [TabHost].
 *
 * `stableKey` is used in route composition and saveable state restoration, so it should remain
 * stable across process death and app upgrades.
 *
 * A tab host should use a finite set of feature tabs. Do not create a new [TabId] for each content
 * item or navigation destination: visited tab stores intentionally remain in their
 * [ViewModelStoreOwner] so tab state can be restored.
 *
 * Example:
 * ```kotlin
 * enum class ProfileTab(
 *     override val stableKey: String,
 *     val label: String
 * ) : TabId {
 *     Uploads("uploads", "Uploads"),
 *     Liked("liked", "Liked"),
 *     Collections("collections", "Collections")
 * }
 * ```
 *
 * @author Cortland Walker
 */
interface TabId {
    /** Stable persistence and routing key for this tab. */
    val stableKey: String
}

/**
 * Hierarchical route describing where a tab host lives in the overall tab tree.
 *
 * This is mainly used for:
 *
 * - unique store keys for child reducers
 * - nesting app-level tabs and feature-level tabs without collisions
 * - preserving `rememberSaveable` state per tab scene
 *
 * Example:
 * ```kotlin
 * val root = TabRoute.Root
 * val profileRoute = root.child(AppTab.Profile)
 * val uploadsRoute = profileRoute.child(ProfileTab.Uploads)
 * ```
 *
 * @property segments ordered route segments from root to current tab scene.
 * @author Cortland Walker
 */
data class TabRoute(
    val segments: List<String>
) {
    /**
     * Flattened route key suitable for saveable-state and ViewModel keys.
     *
     * `Root` resolves to `"root"`.
     */
    val stableKey: String
        get() = if (segments.isEmpty()) "root" else segments.joinToString("/")

    /**
     * Returns a child route by appending the provided [tabId].
     *
     * @author Cortland Walker
     */
    fun child(tabId: TabId): TabRoute = TabRoute(segments + tabId.stableKey)

    companion object {
        /** Root route for a top-level tab host. */
        val Root = TabRoute(emptyList())
    }
}

/**
 * Whether a tab scene is currently visible to the user.
 *
 * Reducers can use this as a cue to start or stop expensive work.
 *
 * @author Cortland Walker
 */
enum class TabVisibilityState {
    Visible,
    Hidden
}

/**
 * Lifecycle-style events synthesized by [TabHost] for [TabChildReducer] instances.
 *
 * @author Cortland Walker
 */
enum class TabLifecycleEvent {
    /** The tab became active for the first time. */
    Selected,

    /** The active tab was tapped/selected again while already active. */
    Reselected,

    /** The tab became active again after previously being visited. */
    Restored,

    /** The tab left the screen. */
    Hidden
}

/**
 * Explicit tab lifecycle metadata passed to [TabLifecycleCallbacks].
 *
 * Example:
 * ```kotlin
 * override fun onTabHidden(context: TabLifecycleContext): FeedAction? {
 *     return FeedAction.TabHidden
 * }
 * ```
 *
 * @property route fully qualified tab route for this scene.
 * @property visibilityState visible/hidden state at the moment the callback fired.
 * @author Cortland Walker
 */
data class TabLifecycleContext(
    val route: TabRoute,
    val visibilityState: TabVisibilityState
)

/**
 * Optional lifecycle callback contract for tab-aware reducers.
 *
 * Returning an action keeps all real behavior inside normal reducer `process(...)` handling
 * instead of splitting logic across imperative callbacks.
 *
 * Example:
 * ```kotlin
 * class NotificationsReducer : TabChildReducer<State, Action, Effect>() {
 *     override fun onTabSelected(context: TabLifecycleContext): Action? = Action.TabVisible
 *     override fun onTabHidden(context: TabLifecycleContext): Action? = Action.TabHidden
 * }
 * ```
 *
 * @author Cortland Walker
 */
interface TabLifecycleCallbacks<A : Any> {
    /** Action to dispatch when a tab is selected for the first time. */
    fun onTabSelected(context: TabLifecycleContext): A? = null

    /** Action to dispatch when the already-selected tab is reselected. */
    fun onTabReselected(context: TabLifecycleContext): A? = null

    /** Action to dispatch when a previously visited tab becomes active again. */
    fun onTabRestored(context: TabLifecycleContext): A? = null

    /** Action to dispatch when a tab leaves the screen. */
    fun onTabHidden(context: TabLifecycleContext): A? = null
}

/**
 * Base reducer for screens that participate in the tab lifecycle.
 *
 * Use this instead of plain [Reducer] when the feature needs awareness of:
 *
 * - first selection
 * - reselection
 * - restoration
 * - hiding/offscreen cleanup
 *
 * Example:
 * ```kotlin
 * class FeedReducer : TabChildReducer<FeedState, FeedAction, FeedEffect>() {
 *     override fun onTabSelected(context: TabLifecycleContext): FeedAction? =
 *         if (currentState.items.isEmpty()) FeedAction.Load else null
 *
 *     override fun onTabReselected(context: TabLifecycleContext): FeedAction? =
 *         FeedAction.TabReselected
 * }
 * ```
 *
 * @author Cortland Walker
 */
abstract class TabChildReducer<S : Any, A : Any, E : Any> :
    Reducer<S, A, E>(),
    TabLifecycleCallbacks<A> {

    internal fun dispatchTabLifecycle(
        event: TabLifecycleEvent,
        context: TabLifecycleContext
    ) {
        val action = when (event) {
            TabLifecycleEvent.Selected -> onTabSelected(context)
            TabLifecycleEvent.Reselected -> onTabReselected(context)
            TabLifecycleEvent.Restored -> onTabRestored(context)
            TabLifecycleEvent.Hidden -> onTabHidden(context)
        }
        action?.let(::postAction)
    }
}

/**
 * Derived scene state passed into a tab's content renderer.
 *
 * This contains only view-facing tab metadata, not the feature's reducer state.
 *
 * @property tabId currently rendered tab id.
 * @property route nested route to this tab scene.
 * @property visibilityState whether the scene is visible.
 * @property selectionCount number of times this tab became active.
 * @property reselectionCount number of active-tab reselections observed so far.
 * @author Cortland Walker
 */
data class TabSceneState<T : TabId>(
    val tabId: T,
    val route: TabRoute,
    val visibilityState: TabVisibilityState,
    val selectionCount: Int,
    val reselectionCount: Int
)

/**
 * Reducer-owned state for a tab host.
 *
 * This is the source of truth for:
 *
 * - current tab selection
 * - tab backstack
 * - first-select vs restore counts
 * - reselection counts
 * - per-tab visibility
 *
 * Example:
 * ```kotlin
 * val state = TabHostState(selectedTabId = AppTab.Home)
 * state.selectedTabId // Home
 * state.backstack     // [Home]
 * ```
 *
 * @author Cortland Walker
 */
data class TabHostState<T : TabId>(
    val selectedTabId: T,
    val backstack: List<T> = listOf(selectedTabId),
    val selectionCounts: Map<T, Int> = mapOf(selectedTabId to 1),
    val reselectionCounts: Map<T, Int> = emptyMap(),
    val visibilityByTabId: Map<T, TabVisibilityState> = mapOf(
        selectedTabId to TabVisibilityState.Visible
    )
) {
    /** Returns how many times [tabId] has been selected or restored. */
    fun selectionCount(tabId: T): Int = selectionCounts[tabId] ?: 0

    /** Returns how many times [tabId] has been reselected while already active. */
    fun reselectionCount(tabId: T): Int = reselectionCounts[tabId] ?: 0

    /** Returns the current visibility for [tabId], defaulting to [TabVisibilityState.Hidden]. */
    fun visibility(tabId: T): TabVisibilityState =
        visibilityByTabId[tabId] ?: TabVisibilityState.Hidden

    /** Returns `true` if [tabId] has ever been selected. */
    fun hasVisited(tabId: T): Boolean = selectionCount(tabId) > 0
}

/**
 * Actions understood by [TabHostReducer].
 *
 * Use [SelectTab] for normal user tab changes, [RestoreTab] when restoring prior tab state
 * without growing history, and [Back] to pop the host-level backstack.
 *
 * @author Cortland Walker
 */
sealed interface TabHostAction<T : TabId> {
    /** Selects a tab and records it in back history. */
    data class SelectTab<T : TabId>(val tabId: T) : TabHostAction<T>

    /** Selects a tab without appending a new backstack entry. */
    data class RestoreTab<T : TabId>(val tabId: T) : TabHostAction<T>

    /** Pops the tab backstack to the previous visited tab, if one exists. */
    class Back<T : TabId> : TabHostAction<T>
}

/**
 * Small app-facing controller that wraps [TabHostState] plus a dispatch function.
 *
 * This exists to make Compose usage ergonomic:
 *
 * ```kotlin
 * val appTabs = rememberTabHostController(
 *     key = "app-tabs",
 *     initialTabId = AppTab.Home
 * )
 *
 * BackHandler(enabled = appTabs.canGoBack) {
 *     appTabs.goBack()
 * }
 * ```
 *
 * @property state latest tab host state snapshot.
 * @author Cortland Walker
 */
@Immutable
data class TabHostController<T : TabId>(
    val state: TabHostState<T>,
    private val postAction: (TabHostAction<T>) -> Unit
) {
    /** Currently selected tab id. */
    val selectedTabId: T
        get() = state.selectedTabId

    /** Whether the host can pop back to a previous tab. */
    val canGoBack: Boolean
        get() = state.backstack.size > 1

    /** Dispatches [TabHostAction.SelectTab]. */
    fun selectTab(tabId: T) {
        postAction(TabHostAction.SelectTab(tabId))
    }

    /** Dispatches [TabHostAction.RestoreTab]. */
    fun restoreTab(tabId: T) {
        postAction(TabHostAction.RestoreTab(tabId))
    }

    /** Dispatches [TabHostAction.Back]. */
    fun goBack() {
        postAction(TabHostAction.Back())
    }

    /** Dispatches an arbitrary [TabHostAction]. */
    fun dispatch(action: TabHostAction<T>) {
        postAction(action)
    }
}

/**
 * Reducer that owns selection, reselection, visibility, and backstack behavior for a tab host.
 *
 * Most apps do not instantiate this directly in Compose. Prefer [rememberTabHostStore] or
 * [rememberTabHostController].
 *
 * Example:
 * ```kotlin
 * val store = rememberTabHostStore(
 *     key = "profile-tabs",
 *     initialTabId = ProfileTab.Uploads
 * )
 * ```
 *
 * @author Cortland Walker
 */
class TabHostReducer<T : TabId>(
    private val defaultTabId: T
) : Reducer<TabHostState<T>, TabHostAction<T>, Nothing>() {

    override fun onLoadAction(): TabHostAction<T>? = null

    override suspend fun process(action: TabHostAction<T>) {
        when (action) {
            is TabHostAction.SelectTab -> selectTab(action.tabId, trackBackstack = true)
            is TabHostAction.RestoreTab -> selectTab(action.tabId, trackBackstack = false)
            is TabHostAction.Back -> goBack()
        }
    }

    private fun selectTab(
        tabId: T,
        trackBackstack: Boolean
    ) {
        val current = currentState
        if (current.selectedTabId == tabId) {
            state {
                it.copy(
                    reselectionCounts = it.reselectionCounts + (
                        tabId to (it.reselectionCount(tabId) + 1)
                    )
                )
            }
            return
        }

        val knownTabs = (
            current.backstack +
                current.selectionCounts.keys +
                current.visibilityByTabId.keys +
                listOf(current.selectedTabId, tabId, defaultTabId)
            )
            .distinct()

        val nextBackstack = if (trackBackstack) {
            current.backstack
                .filterNot { it == tabId }
                .plus(tabId)
        } else {
            current.backstack
                .dropLast(1)
                .filterNot { it == tabId }
                .plus(tabId)
                .ifEmpty { listOf(tabId) }
        }

        state {
            it.copy(
                selectedTabId = tabId,
                backstack = nextBackstack,
                selectionCounts = it.selectionCounts + (tabId to (it.selectionCount(tabId) + 1)),
                visibilityByTabId = knownTabs.associateWith { knownTab ->
                    if (knownTab == tabId) {
                        TabVisibilityState.Visible
                    } else {
                        TabVisibilityState.Hidden
                    }
                }
            )
        }
    }

    private fun goBack() {
        val current = currentState
        if (current.backstack.size <= 1) return

        val nextBackstack = current.backstack.dropLast(1)
        val tabId = nextBackstack.lastOrNull() ?: defaultTabId
        val knownTabs = (
            nextBackstack +
                current.selectionCounts.keys +
                current.visibilityByTabId.keys +
                listOf(defaultTabId, tabId)
            )
            .distinct()

        state {
            it.copy(
                selectedTabId = tabId,
                backstack = nextBackstack,
                selectionCounts = it.selectionCounts + (tabId to (it.selectionCount(tabId) + 1)),
                visibilityByTabId = knownTabs.associateWith { knownTab ->
                    if (knownTab == tabId) {
                        TabVisibilityState.Visible
                    } else {
                        TabVisibilityState.Hidden
                    }
                }
            )
        }
    }
}

/**
 * Declarative registration object describing a tab in a [TabHost].
 *
 * In normal app code, prefer creating instances through [reducerTabSpec] rather than invoking
 * this constructor directly.
 *
 * @property id unique tab identifier.
 * @property title UI-facing title.
 * @property icon optional default icon.
 * @property selectedIcon optional selected-state icon.
 * @author Cortland Walker
 */
class TabSpec<T : TabId>(
    val id: T,
    val title: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = icon,
    internal val content: @Composable (sceneState: TabSceneState<T>) -> Unit
) {
    @Composable
    internal fun Render(sceneState: TabSceneState<T>) {
        content(sceneState)
    }
}

/**
 * Creates a reducer-backed [TabSpec] with minimal boilerplate.
 *
 * This helper:
 *
 * - creates/retrieves a keyed [StoreViewModel] for the tab route
 * - collects state with `collectAsStateWithLifecycle`
 * - binds explicit tab lifecycle callbacks for [TabChildReducer]
 * - forwards one-off effects through [onEffect]
 *
 * Example:
 * ```kotlin
 * fun homeTabSpec(
 *     onNavigate: (Destination) -> Unit
 * ): TabSpec<AppTab> = reducerTabSpec(
 *     id = AppTab.Home,
 *     title = "Home",
 *     initialState = HomeState(),
 *     reducerFactory = { HomeReducer(repository) },
 *     onEffect = { effect ->
 *         if (effect is HomeEffect.NavigateToClip) {
 *             onNavigate(Destination.Clip(effect.clip))
 *         }
 *     }
 * ) { sceneState, state, effects, reducer ->
 *     HomeScreen(
 *         sceneState = sceneState,
 *         state = state,
 *         effects = effects,
 *         onAction = reducer::postAction
 *     )
 * }
 * ```
 *
 * @author Cortland Walker
 */
fun <T : TabId, S : Any, A : Any, E : Any, R : TabChildReducer<S, A, E>> reducerTabSpec(
    id: T,
    title: String,
    icon: ImageVector? = null,
    selectedIcon: ImageVector? = icon,
    initialState: S,
    reducerFactory: () -> R,
    onEffect: suspend (E) -> Unit = {},
    screen: @Composable (
        sceneState: TabSceneState<T>,
        state: S,
        effects: Flow<E>,
        reducer: R
    ) -> Unit
): TabSpec<T> = TabSpec(
    id = id,
    title = title,
    icon = icon,
    selectedIcon = selectedIcon
) { sceneState ->
    val (store, reducer) = rememberStoreViewModel(
        key = "tab:${sceneState.route.stableKey}",
        initialState = initialState,
        reducerFactory = reducerFactory
    )
    val visible = sceneState.visibilityState == TabVisibilityState.Visible
    val state by store.state.collectStateWhen(visible)

    BindTabLifecycle(
        sceneState = sceneState,
        reducer = reducer
    )
    ObserveEffects(
        effects = store.effects,
        collectWhen = visible,
        onEffect = onEffect
    )
    BindReducerLifecycle(reducer, isActive = visible)

    screen(sceneState, state, store.effects, reducer)
}

/**
 * Creates or retrieves the [StoreViewModel] that backs a [TabHostReducer].
 *
 * Use this when you want direct access to the raw store state/effects instead of the simpler
 * [TabHostController] API.
 *
 * Example:
 * ```kotlin
 * val store = rememberTabHostStore(
 *     key = "profile-tabs",
 *     initialTabId = ProfileTab.Uploads
 * )
 * val state by store.state.collectAsStateWithLifecycle()
 * ```
 *
 * @author Cortland Walker
 */
@Composable
fun <T : TabId> rememberTabHostStore(
    key: String,
    initialTabId: T,
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
): StoreViewModel<TabHostState<T>, TabHostAction<T>, Nothing> {
    val (store, _) = rememberStoreViewModel(
        key = key,
        initialState = TabHostState(selectedTabId = initialTabId),
        reducerFactory = { TabHostReducer(initialTabId) },
        viewModelStoreOwner = viewModelStoreOwner
    )
    return store
}

/**
 * Preferred Compose entry point for a reducer-backed tab host.
 *
 * This helper wraps [rememberTabHostStore], collects state with lifecycle awareness, and exposes
 * convenience methods like [TabHostController.selectTab] and [TabHostController.goBack].
 *
 * Example:
 * ```kotlin
 * val controller = rememberTabHostController(
 *     key = "app-tabs",
 *     initialTabId = AppTab.Home
 * )
 *
 * TabHost(
 *     controller = controller,
 *     tabs = tabs
 * )
 * ```
 *
 * @author Cortland Walker
 */
@Composable
fun <T : TabId> rememberTabHostController(
    key: String,
    initialTabId: T,
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
): TabHostController<T> {
    val store = rememberTabHostStore(
        key = key,
        initialTabId = initialTabId,
        viewModelStoreOwner = viewModelStoreOwner
    )
    val state by store.state.collectAsStateWithLifecycle()
    return remember(store, state) {
        TabHostController(
            state = state,
            postAction = store::postAction
        )
    }
}

/**
 * Renders the currently selected tab scene using a [TabHostController].
 *
 * This overload is the one most apps should use.
 *
 * @author Cortland Walker
 */
@Composable
fun <T : TabId> TabHost(
    controller: TabHostController<T>,
    tabs: List<TabSpec<T>>,
    modifier: Modifier = Modifier,
    route: TabRoute = TabRoute.Root
) {
    TabHost(
        state = controller.state,
        tabs = tabs,
        modifier = modifier,
        route = route
    )
}

/**
 * Renders the currently selected tab scene using raw [TabHostState].
 *
 * Use this overload when state is being managed externally and a [TabHostController] would add
 * unnecessary indirection.
 *
 * Nested tab hosts should pass the parent [route] to ensure unique child store keys.
 *
 * Example:
 * ```kotlin
 * TabHost(
 *     state = nestedState,
 *     tabs = nestedTabs,
 *     route = parentSceneState.route
 * )
 * ```
 *
 * @author Cortland Walker
 */
@Composable
fun <T : TabId> TabHost(
    state: TabHostState<T>,
    tabs: List<TabSpec<T>>,
    modifier: Modifier = Modifier,
    route: TabRoute = TabRoute.Root
) {
    val tabsById = remember(tabs) { tabs.associateBy(TabSpec<T>::id) }
    val selectedTab = requireNotNull(tabsById[state.selectedTabId]) {
        "Missing TabSpec for ${state.selectedTabId.stableKey}"
    }
    val childRoute = route.child(selectedTab.id)
    val sceneState = remember(state, childRoute, selectedTab) {
        TabSceneState(
            tabId = selectedTab.id,
            route = childRoute,
            visibilityState = state.visibility(selectedTab.id),
            selectionCount = state.selectionCount(selectedTab.id),
            reselectionCount = state.reselectionCount(selectedTab.id)
        )
    }
    val saveableStateHolder = rememberSaveableStateHolder()

    Box(modifier = modifier) {
        saveableStateHolder.SaveableStateProvider(childRoute.stableKey) {
            selectedTab.Render(sceneState)
        }
    }
}

/**
 * Shared tab lifecycle binding for Compose hosts and Fragment-hosted Compose. Hidden scenes can
 * stay mounted: visibility changes dispatch Hidden exactly once, and a later activation dispatches
 * Restored. [isActive] lets the host include foreground lifecycle or coverage by another screen.
 *
 * Keep this binding composed while hidden. Cumulative reselections observed during inactivity or
 * before mounting are not replayed. Disposal hides an active binding, but does not clear its store.
 * This binds lifecycle actions only; state/effect collection and UI retention remain host policies.
 */
@Composable
fun <S : Any, A : Any, E : Any> BindTabLifecycle(
    sceneState: TabSceneState<*>,
    reducer: TabChildReducer<S, A, E>,
    isActive: Boolean = true,
) {
    val binding = remember(sceneState.route, reducer) {
        TabLifecycleBinding(reducer, sceneState.route, sceneState.reselectionCount)
    }
    LaunchedEffect(binding, sceneState.visibilityState, sceneState.selectionCount,
        sceneState.reselectionCount, isActive) {
        binding.update(sceneState, isActive)
    }
    DisposableEffect(binding) { onDispose { binding.hide() } }
}

private class TabLifecycleBinding<S : Any, A : Any, E : Any>(
    private val reducer: TabChildReducer<S, A, E>,
    private val route: TabRoute,
    private var lastReselection: Int,
) {
    private var visible = false
    private var appeared = false
    private var lastSelection = 0

    fun update(scene: TabSceneState<*>, active: Boolean) {
        val wasVisible = visible
        val reselected = scene.reselectionCount > lastReselection
        lastReselection = scene.reselectionCount
        if (!active || scene.visibilityState != TabVisibilityState.Visible || scene.selectionCount == 0) {
            hide()
        } else {
            val context = TabLifecycleContext(route, TabVisibilityState.Visible)
            if (!wasVisible || lastSelection != scene.selectionCount) {
                val event = if (!appeared && scene.selectionCount == 1) TabLifecycleEvent.Selected
                    else TabLifecycleEvent.Restored
                visible = true
                appeared = true
                reducer.dispatchTabLifecycle(event, context)
            }
            if (wasVisible && reselected) reducer.dispatchTabLifecycle(TabLifecycleEvent.Reselected, context)
        }
        lastSelection = scene.selectionCount
    }

    fun hide() {
        if (!visible) return
        visible = false
        reducer.dispatchTabLifecycle(TabLifecycleEvent.Hidden, TabLifecycleContext(route, TabVisibilityState.Hidden))
    }
}
