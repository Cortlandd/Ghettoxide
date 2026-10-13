# Tab System Guide

This guide focuses on the pieces added for tab-heavy apps.

## Goals

The tab system is designed to make these common problems boring:

- adding a new tab without repeating a lot of wiring
- preserving tab state while switching around
- handling active-tab reselection
- restoring nested tab state inside a screen
- pausing expensive work when a tab is hidden
- avoiding duplicate collectors after repeated tab switches

## The primitives

### `TabId`

Implement `TabId` with a stable key:

```kotlin
enum class AppTab(
    override val stableKey: String,
    val label: String
) : TabId {
    Home("home", "Home"),
    Search("search", "Search")
}
```

Keep the set finite and feature-oriented. Do not create a `TabId` per feed item, profile, or detail
screen. A visited tab's keyed ViewModel intentionally stays in its owner so its reducer state can
be restored; unbounded dynamic tab IDs would therefore create unbounded retained stores.

### `TabSpec`

`TabSpec` is the declarative unit of tab registration.
In normal app code, you should create it through `reducerTabSpec(...)`.

### `TabHostReducer`

`TabHostReducer` owns:

- current selection
- backstack
- per-tab selection counts
- per-tab reselection counts
- visibility metadata

Use it for both:

- app shell tabs
- nested feature tabs

### `TabChildReducer`

This is the base class for reducers that want explicit tab lifecycle callbacks:

- `onTabSelected(...)`
- `onTabRestored(...)`
- `onTabReselected(...)`
- `onTabHidden(...)`

Those callbacks return an action, so the reducer still handles behavior through its normal `process(...)` function.

### Refresh policy and screen lifecycle

`TabChildReducer` also inherits the optional `onStartAction()`, `onResumeAction()`,
`onPauseAction()`, and `onStopAction()` hooks. `reducerTabSpec` binds them automatically using
the local lifecycle and scene visibility. All return `null` by default, leaving existing tab
behavior unchanged. Home demonstrates refresh on every resume:

```kotlin
override fun onLoadAction(): HomeAction? = null
override fun onResumeAction(): HomeAction = HomeAction.Load
```

This covers first display, returning from another tab or an unmounted detail screen, and
background/foreground. A custom host that keeps a tab covered must report that visibility.
Resume is separate from reselection: tapping an already-active tab still uses `onTabReselected`.
Apps can retain cached content by leaving resume unimplemented, refresh specifically on
`onTabRestored`, or dispatch a freshness check from resume. Pick one load trigger rather than
loading from both a tab callback and a screen lifecycle callback.

For a standalone Compose host, use `BindReducerLifecycle(reducer, isActive = hostVisible)`.
Fragment hosts configure `reducerLifecycleEnabled` on the base Fragment. Do not add another
binding inside `reducerTabSpec` or `ReducerFragment`; each already owns one. State/effect collection
and lifecycle callbacks remain separate policies. See [the lifecycle contract](../DOC.md#presentation-lifecycle-actions).

## App-level setup

```kotlin
val appTabs = rememberTabHostController(
    key = "app-shell-tabs",
    initialTabId = AppTab.Home
)

val tabs = listOf(
    homeTabSpec(onNavigate),
    searchTabSpec(onNavigate),
    notificationsTabSpec(onNavigate),
    profileTabSpec(onNavigate)
)

TabHost(
    controller = appTabs,
    tabs = tabs
)
```

## Nested setup

Inside a parent screen:

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

That `route` propagation is what keeps nested tab paths explicit.

`rememberTabHostController(...)` is the preferred app-facing helper because it wraps the
lifecycle-aware collection step and exposes convenience methods like `selectTab(...)`,
`restoreTab(...)`, and `goBack()`.

## Forward navigation from a tab

Tabs solve lateral navigation between sibling sections. A detail screen is a different layer.

Recommended pattern:

- tab reducers emit a typed effect such as `NavigateToClip(clip)`
- the app shell receives that effect and pushes a typed route onto its own backstack
- the shell renders the detail screen above the tab host
- the detail screen gets the object directly from the route payload

That keeps reducers free of `NavController`, `Context`, `View`, or fragment references.

Example from `tabshowcase`:

```kotlin
sealed interface HomeEffect {
    data class NavigateToClip(val clip: ClipCard) : HomeEffect
}

onEffect = { effect ->
    if (effect is HomeEffect.NavigateToClip) {
        onNavigate(
            DemoDestination.Clip(
                clip = effect.clip,
                originLabel = "Home"
            )
        )
    }
}
```

Then the shell owns the stack:

```kotlin
sealed interface ShowcaseShellAction {
    data class NavigateTo(val destination: DemoDestination) : ShowcaseShellAction
    data object Back : ShowcaseShellAction
}
```

This is usually cleaner than trying to make tab reducers understand the full app graph.

### If you use Navigation Compose

The same separation still applies:

- tabs emit typed effects
- the shell converts them into `navController.navigate(...)`
- pass a stable id when the object is large or should be reloaded
- pass a serialized object only when the payload is lightweight and the ergonomics are worth it

For a `ShortoftheWeek`-style flow, the shell can encode a video object plus an origin label into the route. The important part is that the encoding happens at the shell boundary, not inside every tab reducer.

### Sending data back

For results coming back from detail to a tab, prefer one of these:

- update shared repository state and let the tab reducer observe it
- dispatch a typed result action from the shell back into the originating feature store

Avoid passing callbacks or UI objects through reducer state.

## Adding a new tab

1. Create a `TabId`.
2. Create `State`, `Action`, and `Effect` types.
3. Create a `TabChildReducer`.
4. Create a `reducerTabSpec(...)`.
5. Add it to the `tabs` list for the relevant host.

Example:

```kotlin
fun inboxTabSpec(
    onNavigate: (Destination) -> Unit
): TabSpec<AppTab> = reducerTabSpec(
    id = AppTab.Inbox,
    title = "Inbox",
    initialState = InboxState(),
    reducerFactory = { InboxReducer(repo) },
    onEffect = { effect ->
        if (effect is InboxEffect.OpenThread) {
            onNavigate(Destination.Thread(effect.id))
        }
    }
) { sceneState, state, effects, reducer ->
    InboxScreen(
        sceneState = sceneState,
        state = state,
        effects = effects,
        onAction = reducer::postAction
    )
}
```

## Reselection behavior

Reselection is treated differently from first selection or restore.

Typical use:

- active bottom tab tapped again
- emit `ScrollToTop`
- optionally trigger a refresh action

Example:

```kotlin
override fun onTabReselected(context: TabLifecycleContext): FeedAction? {
    return FeedAction.TabReselected
}

override suspend fun process(action: FeedAction) {
    when (action) {
        FeedAction.TabReselected -> emit(FeedEffect.ScrollToTop)
    }
}
```

## Restoring state

There are two layers of restoration:

### Reducer state restoration

Keep durable UI state in reducer state:

- search query
- selected filter
- list scroll position
- pagination cursor
- loaded items

The sample does this by storing `ListPosition` in reducer state and recreating `LazyListState` from it.

### Compose saveable restoration

`TabHost(...)` uses `SaveableStateHolder`, so `rememberSaveable` values inside a tab survive tab switches too.

This is useful for lightweight purely-local UI state.

## Lifecycle-safe collection

### Mounted but hidden tabs

These controls are available since 1.2.0. `TabHost` continues to render only the
selected scene and save its saveable values; these APIs do not automatically retain inactive views.

Fragment-hosted Compose and custom presentation hosts can reuse the same binding as `reducerTabSpec`:

```kotlin
val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
val hostActive = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !coveredByDetail
val collect = hostActive && scene.visibilityState == TabVisibilityState.Visible

BindTabLifecycle(scene, reducer, isActive = hostActive)
BindReducerLifecycle(reducer, isActive = collect)
StoreContent(store, collectWhen = collect, minActiveState = Lifecycle.State.RESUMED) { state, dispatch ->
    FeedScreen(state, dispatch)
}
ObserveEffects(store.effects, collectWhen = collect, minActiveState = Lifecycle.State.RESUMED) {
    handleEffect(it)
}
```

The example belongs to a custom Compose host. In a `ReducerFragment`, configure its
`reducerLifecycleEnabled` and `effectCollectionEnabled` StateFlows plus `effectMinActiveState`
instead of adding another lifecycle binding or collecting the primary store's effects again.
The host must update activity even while state rendering is gated.

Keep `BindTabLifecycle` in composition while inactive. It sends Hidden once on the transition out,
Restored on return, and Hidden on active disposal. Initially hidden scenes receive no activation
callback; store creation and its one-time load remain independent of this binding.
Previously accumulated reselections are not replayed on mount or return. Replacing the reducer or
route ends the old binding and creates a new one. Lifecycle callbacks dispatch normal reducer actions.

`reducerTabSpec` gates its state and primary effects using scene visibility. Screen-specific effect
collectors must also opt in; the Home showcase demonstrates this for ScrollToTop. Visibility does
not itself stop animations or reducer jobs. Handle Hidden actions to pause expensive feature work.

`collectWhen = false` retains the last state snapshot without subscribing; returning catches up to
the latest value. Effects emitted without a subscriber are not queued by these APIs. Persist a
pending result in feature state if it must be handled after restoration.

### Use reducer scope helpers for long-lived work

```kotlin
collectInScopeLatest(
    key = "live-pulse",
    flow = repository.observeUnreadPulse(),
    onEach = { pulse -> state { it.copy(livePulseCount = pulse) } }
)
```

### Cancel work when hidden

```kotlin
override fun onTabHidden(context: TabLifecycleContext): NotificationsAction? {
    return NotificationsAction.TabHidden
}

override suspend fun process(action: NotificationsAction) {
    when (action) {
        NotificationsAction.TabHidden -> cancelScopedJob("live-pulse")
    }
}
```

## Leak testing

`app` and `tabshowcase` include LeakCanary through `debugImplementation`, so release artifacts are
unaffected. A useful manual regression pass is:

1. Open every app-level and nested tab.
2. Switch between them repeatedly and reselect the active tab.
3. Open and close several detail destinations.
4. Rotate once on a tab and once on a detail screen.
5. Background and foreground the app, then leave the Activity.

LeakCanary should not report retained Activities, Fragments, Fragment views, or ViewModels. The
notifications pulse should stop immediately when Notifications becomes hidden and restart with a
single collector when restored.

### Keep effects in UI code

Reducers should emit effects such as:

- `ScrollToTop`
- `OpenClipDetails`
- `OpenSettings`

Compose screens should collect and act on them:

```kotlin
ObserveEffects(effects) { effect ->
    if (effect is FeedEffect.ScrollToTop) {
        listState.animateScrollToItem(0)
    }
}
```

## Pitfalls this system is trying to prevent

- starting duplicate collectors every time a tab becomes visible
- keeping background work alive when a media-heavy tab is hidden
- storing navigation or Android UI objects in reducers
- losing nested tab state when switching away and back
- reloading the entire screen on every configuration change

If a new abstraction does not reduce one of those real pain points, it probably does not belong here.
