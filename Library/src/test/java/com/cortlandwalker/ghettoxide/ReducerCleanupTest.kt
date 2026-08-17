package com.cortlandwalker.ghettoxide

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private sealed interface CleanupAction {
    data object QueueCollector : CleanupAction
    data object StartJob : CleanupAction
}

private data object CleanupEffect

private class CleanupReducer : Reducer<Int, CleanupAction, CleanupEffect>() {
    var cleanupHookCalled = false
    var scopedJobStarted = false
    var scopedJobCancelled = false

    override fun onLoadAction(): CleanupAction? = null

    override suspend fun process(action: CleanupAction) {
        when (action) {
            CleanupAction.QueueCollector -> collectLocalOnce(
                key = "items",
                flow = kotlinx.coroutines.flow.emptyFlow<Int>(),
                onEach = {}
            )

            CleanupAction.StartJob -> launchInScope(key = "live-work") {
                scopedJobStarted = true
                try {
                    awaitCancellation()
                } finally {
                    scopedJobCancelled = true
                }
            }
        }
    }

    // Intentionally omits super to verify base cleanup cannot be bypassed.
    override fun onCleared() {
        cleanupHookCalled = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReducerCleanupTest {

    @Test
    fun `pending collectors are bounded by key and released on clear`() = runTest {
        val reducer = CleanupReducer()
        reducer.testBind(initialState = 0, scope = this)

        reducer.accept(CleanupAction.QueueCollector)
        reducer.accept(CleanupAction.QueueCollector)

        assertThat(reducer.pendingCollectorCount).isEqualTo(1)

        reducer.clear()

        assertThat(reducer.pendingCollectorCount).isEqualTo(0)
        assertThat(reducer.cleanupHookCalled).isTrue()
    }

    @Test
    fun `clear cancels keyed jobs when override does not call super`() = runTest {
        val reducer = CleanupReducer()
        reducer.testBind(initialState = 0, scope = this)

        reducer.accept(CleanupAction.StartJob)
        runCurrent()
        assertThat(reducer.scopedJobStarted).isTrue()

        reducer.clear()
        runCurrent()

        assertThat(reducer.cleanupHookCalled).isTrue()
        assertThat(reducer.scopedJobCancelled).isTrue()
    }
}
