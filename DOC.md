# Ghettoxide Documentation

Ghettoxide is a compact reducer architecture for Android that now supports both single-screen stores and reducer-driven tab containers.

## Core architecture

### `Reducer<S, A, E>`

The reducer is still the main place for business logic:

- read `currentState`
- update state with `state { ... }`
- emit one-off effects with `emit(...)`
- enqueue more work with `postAction(...)`

Action processing is serialized behind a mutex, so reducer logic stays ordered and deterministic.

### `StoreViewModel<S, A, E>`

`StoreViewModel` owns:

- immutable `StateFlow<S>`
- `SharedFlow<E>` for one-off effects
- the reducer instance
- the reducer's long-lived coroutine scope

`onLoadAction()` is dispatched exactly once when the store is created.

### Compose helpers

- `StoreContent(store) { state, dispatch -> ... }`
- `ObserveEffects(effects) { effect -> ... }`
- `rememberStoreViewModel(...)`

These helpers all use lifecycle-aware collection patterns so screens do not quietly accumulate collectors.

## Tab architecture

### Main types

- `TabId`: stable identifier for a tab
- `TabRoute`: path for root and nested tabs
- `TabVisibilityState`: `Visible` or `Hidden`
- `TabLifecycleContext`: route plus visibility metadata
- `TabChildReducer`: reducer base class for tab content
- `TabSpec`: declarative tab definition
- `TabHostState`: selected tab, backstack, selection counts, visibility
- `TabHostAction`: `SelectTab`, `RestoreTab`, `Back`
- `TabHostReducer`: reducer that drives shell-level tab state

### Why selection counts matter

`TabHostReducer` tracks more than "currently selected":

- first selection triggers `onTabSelected(...)`
- reselection increments a separate reselection count and triggers `onTabReselected(...)`
- returning to an already-visited tab increments selection count again and triggers `onTabRestored(...)`

That lets tabs implement different behavior for:

- first load
- return from another tab
- tapping the active tab again

### How child tabs are wired

`reducerTabSpec(...)` keeps tab setup small:

```kotlin
fun notificationsTabSpec(
    onNavigate: (Destination) -> Unit
): TabSpec<AppTab> = reducerTabSpec(
    id = AppTab.Notifications,
    title = "Notifications",
    initialState = NotificationsState(),
    reducerFactory = { NotificationsReducer(repo) },
    onEffect = { effect ->
        if (effect is NotificationsEffect.NavigateToNotification) {
            onNavigate(Destination.Notification(effect.id))
        }
    }
) { sceneState, state, effects, reducer ->
    NotificationsScreen(
        sceneState = sceneState,
        state = state,
        effects = effects,
        onAction = reducer::postAction
    )
}
```

Each tab gets:

- its own reducer
- its own `StoreViewModel`
- its own saved Compose state bucket
- lifecycle callbacks based on visibility/selection changes

## Lifecycle and memory rules

### What belongs in reducers

Put these in reducer state and actions:

- query text
- selected filters
- pagination cursor
- scroll index and offset
- loading/error state
- refresh intent
- analytics or domain decisions

### What does **not** belong in reducers

Do not store:

- `Context`
- `View`
- `Fragment`
- `NavController`
- player instances
- adapters
- composable lambdas

Those belong in Compose/UI/lifecycle code.

Reducers should emit effects instead, for example:

- `OpenSettings`
- `OpenClipDetails`
- `ScrollToTop`
- `ShowSnackbar`

### View lifecycle vs ViewModel lifecycle

Ghettoxide now separates them more clearly:

- `onViewDetached()` is for view-bound cleanup
- `onCleared()` is for final `ViewModel` cleanup

That prevents reducers from being "cleared" just because a view was recreated.

### Presentation lifecycle actions

Refresh frequency belongs to the application. These optional reducer methods return actions for
the normal serialized `process(...)` path; all default to `null`:

| Hook | When it runs | Example use |
| --- | --- | --- |
| `onStartAction()` | Active presentation enters STARTED | Subscribe to live data |
| `onResumeAction()` | Active presentation enters RESUMED | Refresh on every appearance |
| `onPauseAction()` | Resumed presentation pauses, hides, or is unbound | Pause interactive work |
| `onStopAction()` | Started presentation stops, hides, or is unbound | Cancel a presentation-only job |

The initial display is a start/resume too. Background/foreground, view recreation, and returning
to a retained hidden presentation can produce another pair. An already-resumed owner catches up
with start then resume when bound. Pausing alone produces pause/resume without another start.
Hiding or disposal balances resume with pause and start with stop. Recomposition adds no events.

For refresh on every appearance, choose resume as the load trigger:

```kotlin
override fun onLoadAction(): FeedAction? = null
override fun onResumeAction(): FeedAction = FeedAction.Refresh
```

For one-time loading, return `FeedAction.Load` from `onLoadAction()` and leave the new hooks alone.
For stale-only refresh, return a freshness-check action from resume and make the decision in
`process`. A live screen can start a keyed job from start and cancel it from stop. The framework
does not impose a cache age, timer, request deduplication, or cancellation policy. Keep those
decisions in the owning reducer. Avoid requesting the same load from startup, resume, and tab
selection together. Existing tab hooks remain useful for tab-specific selection/reselection work.

`ReducerFragment` binds to its view lifecycle automatically. `reducerTabSpec` binds to the local
lifecycle and scene visibility. A custom Compose host installs one binding:

```kotlin
BindReducerLifecycle(reducer, isActive = visible && !coveredByDetail)
StoreContent(store, collectWhen = visible && !coveredByDetail) { state, dispatch ->
    FeedScreen(state, dispatch)
}
```

Do not install an additional binding inside `ReducerFragment` or a `reducerTabSpec` screen. For
a shared store, designate one presentation owner. A custom host must report hidden/covered
status; the Activity lifecycle alone cannot identify which of its mounted screens is visible.
Binding lifecycle actions does not change state/effect collection or attach view-local collectors.
Pause/stop actions use the store queue and can run after an in-flight action finishes. Final
resource cleanup must still use `onCleared()` because a cleared store cannot process more actions.

### Retained presentation activity

View existence and user-visible activity are separate. A retained Fragment can keep its view while
its effect collector is gated by a host-owned `StateFlow<Boolean>`:

```kotlin
private val presentationActive = MutableStateFlow(false)
override val reducerLifecycleEnabled: StateFlow<Boolean> = presentationActive
override val effectCollectionEnabled: StateFlow<Boolean> = presentationActive
override val effectMinActiveState = Lifecycle.State.RESUMED
```

Update the gate from the host's selected/covered state. The base Fragment still owns exactly one
primary effect collector and one reducer lifecycle binding; do not add duplicates. The lifecycle
and effect gates can also be overridden independently. Use `ReducerContent(collectWhen = active,
minActiveState = Lifecycle.State.RESUMED) { state, reducer -> ... }` to gate state observation too.
Keep the binding composed while inactive. Gating subscriptions does not cancel the reducer's
ongoing jobs or every Compose animation. Pause feature work through the new pause/stop actions
or `BindTabLifecycle`/tab actions.

Hidden retained Fragments are commonly capped at STARTED. Capping a normal Fragment at CREATED
destroys its view. Keeping a destroyed ComposeView is not a substitute for a valid view lifecycle.

The original collection overloads retain their STARTED behavior. Inactive state collectors keep
their last snapshot and catch up when active; switching stores gets the new store's own initial
snapshot. Effects have no added buffering or replay. Store results that must survive inactivity in
state and acknowledge them after handling, rather than replaying navigation or toast events.

### Preventing duplicate collectors

For long-lived reducer work, use keyed jobs:

```kotlin
collectInScopeLatest(
    key = "notifications-live-pulse",
    flow = repository.observeUnreadPulse(),
    onEach = { pulse -> state { it.copy(livePulseCount = pulse) } }
)
```

If the same tab becomes visible again, the old collector is replaced instead of duplicated.

Pending view collectors are also bounded by key. If an action requests the same collector while a
Fragment view is absent, Ghettoxide keeps one callback rather than retaining an unbounded queue.

### Runtime leak detection

Both runnable sample apps include LeakCanary in debug builds. Run `app` or `tabshowcase`, switch
tabs repeatedly, open and close details, rotate the device, and background/foreground the app.
LeakCanary watches destroyed Activities, Fragments, Fragment views, and cleared ViewModels.

For Fragment-hosted Compose, dispose the composition with the view lifecycle:

```kotlin
ComposeView(requireContext()).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { /* screen */ }
}
```

Tab IDs must represent a finite set of feature tabs. Content IDs belong in navigation routes, not
in dynamically-created `TabId` instances, because visited tab ViewModels are retained intentionally
to preserve state until their `ViewModelStoreOwner` is cleared.

To stop work when a tab hides:

```kotlin
override fun onTabHidden(context: TabLifecycleContext): NotificationsAction? {
    return NotificationsAction.TabHidden
}

override suspend fun process(action: NotificationsAction) {
    when (action) {
        NotificationsAction.TabHidden -> cancelScopedJob("notifications-live-pulse")
    }
}
```

### Compose collection rules

Use:

- `collectAsStateWithLifecycle()` for state
- `ObserveEffects(...)` or `repeatOnLifecycle` for effects
- `rememberTrackedLazyListState(...)` plus reducer actions for scroll restoration

Avoid:

- collecting flows directly in plain `LaunchedEffect(Unit)` without lifecycle gating
- starting heavyweight work from composables without a reducer action or explicit lifecycle owner

## App-level tabs

Use one `TabHostReducer` at the app shell:

```kotlin
val appTabs = rememberTabHostController(
    key = "app-shell-tabs",
    initialTabId = AppTab.Home
)

BackHandler(enabled = appTabs.canGoBack) {
    appTabs.goBack()
}
```

That gives:

- current tab selection
- active-tab reselection counts
- restore counts for revisited tabs
- tab back behavior without pulling navigation objects into reducers

## Nested tabs

Nested tabs work the same way inside a screen:

```kotlin
val nestedTabs = rememberTabHostController(
    key = "profile-nested-tabs",
    initialTabId = ProfileTab.Uploads
)

TabHost(
    controller = nestedTabs,
    tabs = nestedSpecs,
    route = parentSceneState.route
)
```

This is intentionally the same mental model as the app shell.

## Testing

### Reducers

Use `testBind(...)` plus `accept(...)`:

```kotlin
val reducer = UploadsReducer(repository)
reducer.testBind(initialState = UploadsState())

reducer.accept(UploadsAction.Load)

assertThat(reducer.currentState.clips).isNotEmpty()
```

### Tab containers

`TabHostReducer` is a normal reducer and can be tested the same way.
The repo includes unit coverage for:

- selection
- reselection
- back behavior
- one-time `onLoadAction()` dispatch

## Where to look in the sample

- App shell: `tabshowcase/src/main/java/com/cortlandwalker/tabshowcase/AppNavigation.kt`
- Root tabs: `tabshowcase/src/main/java/com/cortlandwalker/tabshowcase/features/home/HomeScreen.kt`, `features/search/SearchScreen.kt`, `features/notifications/NotificationsScreen.kt`, `features/profile/ProfileScreen.kt`
- Fake repositories: `tabshowcase/src/main/java/com/cortlandwalker/tabshowcase/data/demo/FakeKlipyRepository.kt`
- Shared tab primitives: `Library/src/main/java/com/cortlandwalker/ghettoxide/Tabs.kt`
