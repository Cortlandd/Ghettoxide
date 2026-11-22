package com.cortlandwalker.ghettoxide

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationScopeTest {
    private val main = StandardTestDispatcher()
    private val owners = mutableListOf<TestLifecycleOwner>()

    @Before fun setUp() { Dispatchers.setMain(main) }
    @After fun tearDown() {
        owners.forEach { it.registry.currentState = Lifecycle.State.DESTROYED }
        Dispatchers.resetMain()
    }

    @Test fun `current identity accepts synchronous commands and completes UI work normally`() = runTest {
        val owner = owner()
        val identity = MutableStateFlow<String?>("entry-a")
        val scope = PresentationScope(owner, "entry-a", identity)
        var commits = 0
        assertThat(scope.isCurrent).isTrue()
        assertThat(scope.runIfCurrent { commits++ }).isTrue()
        val job = scope.launchWhileCurrent { scope.runIfCurrent { commits++ } }
        runCurrent()

        assertThat(scope.active.value).isTrue()
        assertThat(job?.isCompleted).isTrue()
        assertThat(commits).isEqualTo(2)
    }

    @Test fun `replacement identity revokes old commands while the same owner stays started`() = runTest {
        val owner = owner()
        val identity = MutableStateFlow<String?>("entry-a")
        val original = PresentationScope(owner, "entry-a", identity)
        val originalActive = original.active
        runCurrent()
        identity.value = "entry-b"
        // Validation is fresh even before the flow watcher processes the new identity.
        assertThat(original.isCurrent).isFalse()
        assertThat(original.runIfCurrent { error("Stale command") }).isFalse()
        val replacement = PresentationScope(owner, "entry-b", identity)
        assertThat(replacement.isCurrent).isTrue()
        runCurrent()

        assertThat(originalActive.value).isFalse()
        assertThat(owner.lifecycle.currentState).isEqualTo(Lifecycle.State.STARTED)
    }

    @Test fun `clearing identity cancels transient work without replay when identity returns`() = runTest {
        val identity = MutableStateFlow<String?>("entry-a")
        val scope = PresentationScope(owner(), "entry-a", identity)
        var starts = 0
        var cancellations = 0
        val job = scope.launchWhileCurrent {
            starts++
            try { awaitCancellation() } finally { cancellations++ }
        }
        runCurrent()
        identity.value = null
        runCurrent()
        assertThat(job?.isCancelled).isTrue()
        assertThat(scope.active.value).isFalse()
        assertThat(scope.launchWhileCurrent { error("Inactive launch") }).isNull()
        identity.value = "entry-a"
        runCurrent()

        assertThat(scope.active.value).isTrue()
        assertThat(starts).isEqualTo(1)
        assertThat(cancellations).isEqualTo(1)
    }

    @Test fun `replacing an identity cancels its suspended callback before it can commit`() = runTest {
        val identity = MutableStateFlow<String?>("entry-a")
        val scope = PresentationScope(owner(), "entry-a", identity)
        val result = CompletableDeferred<Unit>()
        var commits = 0
        val job = scope.launchWhileCurrent {
            result.await()
            scope.runIfCurrent { commits++ }
        }
        runCurrent()
        identity.value = "entry-b"
        runCurrent()
        result.complete(Unit)
        runCurrent()

        assertThat(job?.isCancelled).isTrue()
        assertThat(scope.active.value).isFalse()
        assertThat(commits).isEqualTo(0)
    }

    @Test fun `synchronous ownership refresh cancels away and back before identity flow dispatch`() = runTest {
        val identity = MutableStateFlow<String?>("entry-a")
        val scope = PresentationScope(owner(), "entry-a", identity)
        var starts = 0
        var cancellations = 0
        val job = scope.launchWhileCurrent {
            starts++
            try { awaitCancellation() } finally { cancellations++ }
        }
        runCurrent()

        identity.value = null
        scope.refreshOwnership()
        assertThat(scope.active.value).isFalse()
        assertThat(job?.isCancelled).isTrue()
        identity.value = "entry-a"
        scope.refreshOwnership()
        assertThat(scope.active.value).isTrue()
        // Neither identity update has reached the flow watcher yet.
        runCurrent()

        assertThat(scope.isCurrent).isTrue()
        assertThat(starts).isEqualTo(1)
        assertThat(cancellations).isEqualTo(1)
        assertThat(job?.isCancelled).isTrue()
    }

    @Test fun `rapid stop and resume cancels old work even when latest lifecycle state is unchanged`() = runTest {
        val owner = owner(Lifecycle.State.RESUMED)
        val identity = MutableStateFlow<String?>("entry-a")
        val scope = PresentationScope(owner, "entry-a", identity)
        var starts = 0
        var cancellations = 0
        val job = scope.launchWhileCurrent {
            starts++
            try { awaitCancellation() } finally { cancellations++ }
        }
        runCurrent()
        owner.registry.currentState = Lifecycle.State.CREATED
        assertThat(scope.isCurrent).isFalse()
        assertThat(scope.active.value).isFalse()
        assertThat(job?.isCancelled).isTrue()
        // Resume before dispatching queued flow work, as happens during a fast lifecycle round trip.
        owner.registry.currentState = Lifecycle.State.RESUMED
        runCurrent()

        assertThat(scope.active.value).isTrue()
        assertThat(starts).isEqualTo(1)
        assertThat(cancellations).isEqualTo(1)
    }

    @Test fun `destroying the owner invalidates activity and cancels jobs permanently`() = runTest {
        val owner = owner()
        val scope = PresentationScope(owner, "entry-a", MutableStateFlow<String?>("entry-a"))
        var cancelled = false
        val job = scope.launchWhileCurrent {
            try { awaitCancellation() } finally { cancelled = true }
        }
        runCurrent()
        owner.registry.currentState = Lifecycle.State.DESTROYED
        runCurrent()

        assertThat(scope.isCurrent).isFalse()
        assertThat(scope.active.value).isFalse()
        assertThat(job?.isCancelled).isTrue()
        assertThat(cancelled).isTrue()
        assertThat(scope.runIfCurrent { error("Destroyed command") }).isFalse()
        assertThat(scope.launchWhileCurrent { error("Destroyed launch") }).isNull()
    }

    @Test fun `minimum lifecycle can require resumed without changing the default started contract`() = runTest {
        val owner = owner()
        val identity = MutableStateFlow<String?>("entry-a")
        val scope = PresentationScope(owner, "entry-a", identity, minActiveState = Lifecycle.State.RESUMED)
        val active = scope.active
        runCurrent()
        assertThat(scope.isCurrent).isFalse()
        assertThat(active.value).isFalse()
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertThat(scope.isCurrent).isTrue()
        assertThat(active.value).isTrue()
        owner.registry.currentState = Lifecycle.State.STARTED
        assertThat(scope.isCurrent).isFalse()
        assertThat(active.value).isFalse()
    }

    @Test fun `fresh owner validation rejects commands even before adapter flow catches up`() = runTest {
        var ownerCurrent = true
        val scope = PresentationScope(owner(), "entry-a", MutableStateFlow<String?>("entry-a"),
            isOwnerCurrent = { ownerCurrent })
        val active = scope.active
        runCurrent()
        assertThat(active.value).isTrue()
        ownerCurrent = false

        assertThat(scope.isCurrent).isFalse()
        assertThat(scope.runIfCurrent { error("Obsolete host command") }).isFalse()
        assertThat(scope.launchWhileCurrent { error("Obsolete host launch") }).isNull()
    }

    private fun owner(state: Lifecycle.State = Lifecycle.State.STARTED) = TestLifecycleOwner().also {
        owners += it
        it.registry.currentState = state
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
