# Contributor Guide

This repo aims to stay explicit and understandable.

## Architecture guardrails

- Keep reducers small and honest.
- Prefer plain sealed actions/effects over clever generic dispatch.
- Add abstractions only when they remove real repeated wiring.
- Do not move Android UI objects into reducers or `StoreViewModel`.

## Reducer rules

Reducers may own:

- immutable feature state
- domain decisions
- loading and pagination flow
- derived view state
- effect emission

Reducers should not own:

- `Context`
- `View`
- `Fragment`
- `NavController`
- `SnackbarHostState`
- media player instances

Those belong in Compose or lifecycle-aware UI code.

## Tab rules

Use `TabHostReducer` plus `reducerTabSpec(...)` when:

- the feature is selected from a tab bar
- the tab should preserve its own list/query/filter state
- active-tab reselection matters
- hidden tabs should pause work

Do not build a second tab framework next to these APIs unless there is a very clear gap.

## Lifecycle rules

- `collectAsStateWithLifecycle()` for state
- `ObserveEffects(...)` or `repeatOnLifecycle` for effects
- keyed reducer jobs for long-lived flow work
- `onViewDetached()` for view cleanup
- `onCleared()` for final store cleanup
- `rememberUpdatedState` when a long-lived Compose effect invokes a changing callback
- `DisposeOnViewTreeLifecycleDestroyed` for Compose hosted in a Fragment view
- finite feature-level `TabId` sets; never use content IDs as dynamic tabs

Debug builds of both sample apps include LeakCanary. Exercise tab switches, nested tabs, detail
navigation, rotation, and Activity teardown when changing lifecycle-sensitive code.

## Testing expectations

When changing reducers or tab behavior, add or update unit tests for:

- state transitions
- emitted effects
- backstack behavior for tabs
- one-time startup behavior where relevant

## Example app expectations

The `tabshowcase/` module is intentionally part of the docs for the tab system.
The `app/` module is the smaller baseline reducer demo.

If you change the tab APIs:

- keep the sample compiling
- update the sample to demonstrate the new happy path
- update `README.md`, `DOC.md`, and `docs/tabs.md`
