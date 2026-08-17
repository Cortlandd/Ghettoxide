# Changelog

## 1.1.1

- Prevent long-lived Compose collectors from retaining stale callbacks.
- Scope reducer creation to `ViewModelStoreOwner` changes.
- Bound pending view collectors and guarantee reducer job and reference cleanup.
- Harden tab lifecycle callbacks against stale reducer and reselection events.
- Add debug-only LeakCanary coverage and cleanup regression tests.

## 1.1.0

- Add reducer-driven app-level and nested tab hosts.
- Preserve reducer and saveable UI state across tab switches.
- Add explicit selection, reselection, restoration, visibility, and tab back behavior.
- Add lifecycle-safe Compose store helpers and keyed reducer-scope jobs.
- Add the runnable `tabshowcase` application module and tab architecture documentation.
