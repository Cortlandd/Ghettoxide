package com.cortlandwalker.ghettoxide

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReducerLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `lifecycle actions follow start resume pause and stop without replay on recomposition`() {
        val owner = TestLifecycleOwner()
        val actions = mutableListOf<Phase>()
        val reducer = LifecycleReducer().apply { testBind(0, postedActions = actions) }
        val revision = mutableStateOf(0)
        compose.setContent {
            revision.value
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                BindReducerLifecycle(reducer)
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { revision.value++ }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle {
            assertThat(actions).containsExactly(
                Phase.Start, Phase.Resume, Phase.Pause, Phase.Resume, Phase.Pause, Phase.Stop
            ).inOrder()
        }
    }

    @Test
    fun `inactive retained screen waits for activation and balances every visible interval`() {
        val owner = TestLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val actions = mutableListOf<Phase>()
        val reducer = LifecycleReducer().apply { testBind(0, postedActions = actions) }
        val active = mutableStateOf(false)
        val mounted = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (mounted.value) BindReducerLifecycle(reducer, isActive = active.value)
            }
        }
        compose.runOnIdle {
            assertThat(actions).isEmpty()
            active.value = true
        }
        compose.runOnIdle { active.value = false }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle {
            assertThat(actions).containsExactly(Phase.Start, Phase.Resume, Phase.Pause, Phase.Stop).inOrder()
            active.value = true
        }
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle {
            assertThat(actions).containsExactly(
                Phase.Start, Phase.Resume, Phase.Pause, Phase.Stop,
                Phase.Start, Phase.Resume, Phase.Pause, Phase.Stop
            ).inOrder()
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
            assertThat(actions).hasSize(8)
        }
    }

    @Test
    fun `changing owner or reducer closes the previous lifecycle binding`() {
        val firstOwner = TestLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val secondOwner = TestLifecycleOwner().apply { registry.currentState = Lifecycle.State.STARTED }
        val owner = mutableStateOf(firstOwner)
        val firstActions = mutableListOf<Phase>()
        val secondActions = mutableListOf<Phase>()
        val first = LifecycleReducer().apply { testBind(0, postedActions = firstActions) }
        val second = LifecycleReducer().apply { testBind(0, postedActions = secondActions) }
        val reducer = mutableStateOf(first)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner.value) {
                BindReducerLifecycle(reducer.value)
            }
        }
        compose.runOnIdle { owner.value = secondOwner }
        compose.runOnIdle {
            firstOwner.registry.currentState = Lifecycle.State.CREATED
            firstOwner.registry.currentState = Lifecycle.State.RESUMED
            assertThat(firstActions).containsExactly(
                Phase.Start, Phase.Resume, Phase.Pause, Phase.Stop, Phase.Start
            ).inOrder()
            reducer.value = second
        }
        compose.runOnIdle { secondOwner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle {
            assertThat(firstActions).containsExactly(
                Phase.Start, Phase.Resume, Phase.Pause, Phase.Stop, Phase.Start, Phase.Stop
            ).inOrder()
            assertThat(secondActions).containsExactly(Phase.Start, Phase.Resume).inOrder()
        }
    }

    @Test
    fun `refresh on every resume is independent of one-time store loading`() {
        val owner = TestLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val reducer = object : Reducer<Int, Unit, Nothing>() {
            override fun onLoadAction(): Unit? = null
            override fun onResumeAction() = Unit
            override suspend fun process(action: Unit) { state { it + 1 } }
        }
        val store = StoreViewModel(0, reducer)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { BindReducerLifecycle(reducer) }
        }
        compose.runOnIdle {
            assertThat(store.state.value).isEqualTo(1)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertThat(store.state.value).isEqualTo(2) }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private enum class Phase { Start, Resume, Pause, Stop }

    private class LifecycleReducer : Reducer<Int, Phase, Nothing>() {
        override fun onLoadAction(): Phase? = null
        override fun onStartAction() = Phase.Start
        override fun onResumeAction() = Phase.Resume
        override fun onPauseAction() = Phase.Pause
        override fun onStopAction() = Phase.Stop
        override suspend fun process(action: Phase) = Unit
    }
}
