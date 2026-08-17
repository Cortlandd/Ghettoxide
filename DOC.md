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
