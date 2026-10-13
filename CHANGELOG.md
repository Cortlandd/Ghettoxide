# Changelog

## 1.2.0

- Add optional start, resume, pause, and stop action hooks so apps control their refresh policy.
- Bind screen lifecycle actions automatically in Fragment and tab hosts, with an explicit helper
  for custom Compose hosts and visibility gates for retained presentations.
- Deliver hidden/restored tab transitions while views stay mounted without replaying reselections.
- Allow state and effect collection to pause independently of view destruction.
- Add Fragment and Compose lifecycle regression coverage and a refresh-on-resume Home example.
- Publish the release AAR, sources, and dependency metadata through an explicit JitPack build.
- Include the lifecycle and memory safety improvements recorded under 1.1.1 below.

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
