package com.cortlandwalker.ghettoxide

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns transient UI commands by logical presentation identity and lifecycle, independently of
 * whether a view is still attached during an outgoing animation.
 *
 * [identity] must distinguish presentation instances. The host publishes its currently accepted
 * identity, or null when inactive, through [currentIdentity]. [isOwnerCurrent] performs a fresh
 * host-specific check at every command boundary, including before the identity flow catches up.
 * A host must also update [currentIdentity] when that check changes if it needs reactive activity
 * and cancellation; an arbitrary predicate cannot itself signal changes.
 *
 * This is a UI-owner primitive. Keep submitted writes, durable generation, pending completion
 * acknowledgements, and request-result policy in their actual feature or application owner.
 * It does not create a store, collect reducer effects, or replay interrupted commands.
 *
 * All access, identity updates, and lifecycle changes must occur on the main thread.
 */
@MainThread
class PresentationScope<Identity : Any>(
    private val owner: LifecycleOwner,
    private val identity: Identity,
    private val currentIdentity: StateFlow<Identity?>,
    private val isOwnerCurrent: () -> Boolean = { true },
    private val minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
) {
    init {
        require(minActiveState == Lifecycle.State.CREATED || minActiveState == Lifecycle.State.STARTED ||
            minActiveState == Lifecycle.State.RESUMED) {
            "A presentation requires CREATED, STARTED, or RESUMED as its minimum lifecycle state."
        }
    }

    private val jobs = mutableSetOf<Job>()

    private val activity: MutableStateFlow<Boolean> by lazy {
        val state = MutableStateFlow(isCurrent)
        val observer = LifecycleEventObserver { _, _ -> updateActivity(state) }
        owner.lifecycle.addObserver(observer)
        owner.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            currentIdentity.collect { updateActivity(state) }
        }.invokeOnCompletion {
            owner.lifecycle.removeObserver(observer)
            state.value = false
            cancelJobs()
        }
        state
    }

    /** Reads current identity, lifecycle, and host ownership without relying on a cached flow. */
    val isCurrent: Boolean
        get() = owner.lifecycle.currentState.isAtLeast(minActiveState) &&
            currentIdentity.value == identity && isOwnerCurrent()

    /**
     * Can supply an existing effect gate or an explicit pending-result retry observer.
     * Observation is initialized only when needed, then remains eager until owner destruction;
     * a token used solely by [runIfCurrent] installs no long-lived observer.
     */
    val active: StateFlow<Boolean> by lazy { activity.asStateFlow() }

    /**
     * Immediately update activity and cancel obsolete UI jobs from a native host callback.
     * Call after updating [currentIdentity] when an away-and-back transition could occur before
     * the flow watcher runs. This starts ownership observation on first use; validation-only
     * callers can continue using [isCurrent] or [runIfCurrent] without installing observers.
     */
    fun refreshOwnership() {
        updateActivity(activity)
    }

    /** Validate and commit together on the main thread. The block must not suspend. */
    fun runIfCurrent(action: () -> Unit): Boolean {
        if (!isCurrent) return false
        action()
        return true
    }

    /**
     * Launch UI-only work and cancel it when identity or lifecycle ownership is lost.
     * Returning to this presentation does not restart the block. After its last suspension,
     * use [runIfCurrent] before committing a navigation or other one-off UI command.
     */
    fun launchWhileCurrent(block: suspend () -> Unit): Job? {
        if (!isCurrent) return null
        refreshOwnership()
        if (!isCurrent) return null
        val job = owner.lifecycleScope.launch(start = CoroutineStart.LAZY) { block() }
        jobs += job
        job.invokeOnCompletion { jobs -= job }
        if (isCurrent) job.start() else job.cancel()
        return job
    }

    private fun updateActivity(activity: MutableStateFlow<Boolean>) {
        val current = isCurrent
        activity.value = current
        // Lifecycle events are handled synchronously, including a stop/resume between flow turns.
        if (!current) cancelJobs()
    }

    private fun cancelJobs() {
        jobs.toList().forEach { it.cancel() }
    }
}
