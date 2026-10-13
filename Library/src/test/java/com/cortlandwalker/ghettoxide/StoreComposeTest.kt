package com.cortlandwalker.ghettoxide

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StoreComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `inactive state collection holds its snapshot and catches up when enabled`() {
        val store = StoreViewModel(0, CounterReducer())
        val enabled = mutableStateOf(true)
        var rendered = -1
        compose.setContent {
            StoreContent(store, collectWhen = enabled.value) { state, _ -> SideEffect { rendered = state } }
        }
        compose.runOnIdle { store.postAction(Unit) }
        compose.runOnIdle { assertThat(rendered).isEqualTo(1); enabled.value = false }
        compose.runOnIdle { store.postAction(Unit) }
        compose.runOnIdle { assertThat(store.state.value).isEqualTo(2); assertThat(rendered).isEqualTo(1); enabled.value = true }
        compose.runOnIdle { assertThat(rendered).isEqualTo(2) }
    }

    @Test fun `changing store while inactive never displays another owners snapshot`() {
        val first = StoreViewModel(1, CounterReducer())
        val second = StoreViewModel(9, CounterReducer())
        val store = mutableStateOf(first)
        var rendered = -1
        compose.setContent {
            StoreContent(store.value, collectWhen = false) { state, _ -> SideEffect { rendered = state } }
        }
        compose.runOnIdle { assertThat(rendered).isEqualTo(1); store.value = second }
        compose.runOnIdle { assertThat(rendered).isEqualTo(9) }
    }

    @Test fun `effects stop while disabled and use the latest callback without adding subscribers`() {
        val effects = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val enabled = mutableStateOf(true)
        val label = mutableStateOf("old")
        val received = mutableListOf<String>()
        compose.setContent {
            val currentLabel = label.value
            ObserveEffects(effects, collectWhen = enabled.value) { received += "$currentLabel:$it" }
        }
        compose.runOnIdle { assertThat(effects.subscriptionCount.value).isEqualTo(1); label.value = "new" }
        compose.runOnIdle { effects.tryEmit("visible") }
        compose.runOnIdle { enabled.value = false }
        compose.runOnIdle { assertThat(effects.subscriptionCount.value).isEqualTo(0); effects.tryEmit("hidden") }
        compose.runOnIdle { enabled.value = true }
        compose.runOnIdle { assertThat(effects.subscriptionCount.value).isEqualTo(1); effects.tryEmit("restored") }
        compose.runOnIdle { assertThat(received).containsExactly("new:visible", "new:restored").inOrder() }
    }

    @Test fun `RESUMED policy gates state and effects while composition remains mounted`() {
        val owner = TestLifecycleOwner()
        val store = StoreViewModel(0, CounterReducer())
        val effects = MutableSharedFlow<String>(extraBufferCapacity = 1)
        var rendered = -1
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                StoreContent(store, collectWhen = true, minActiveState = Lifecycle.State.RESUMED) { state, _ ->
                    SideEffect { rendered = state }
                }
                ObserveEffects(effects, collectWhen = true, minActiveState = Lifecycle.State.RESUMED) {}
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED; store.postAction(Unit) }
        compose.runOnIdle { assertThat(rendered).isEqualTo(0); assertThat(effects.subscriptionCount.value).isEqualTo(0) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertThat(rendered).isEqualTo(1); assertThat(effects.subscriptionCount.value).isEqualTo(1) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { assertThat(effects.subscriptionCount.value).isEqualTo(0) }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private class CounterReducer : Reducer<Int, Unit, Nothing>() {
        override fun onLoadAction(): Unit? = null
        override suspend fun process(action: Unit) { state { it + 1 } }
    }
}
