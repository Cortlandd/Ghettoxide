package com.cortlandwalker.ghettoxide

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TabsTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<TabLifecycleEvent>()
    private val reducer = VisibilityReducer().apply { testBind(0, postedActions = events) }
    private val scene = mutableStateOf(scene())
    private val mounted = mutableStateOf(true)
    private val active = mutableStateOf(true)

    @Test fun `mounted tab hides and restores once without replaying reselection`() {
        bind()
        compose.runOnIdle { scene.value = scene.value.copy(reselectionCount = 1) }
        compose.runOnIdle { scene.value = scene.value.copy(visibilityState = TabVisibilityState.Hidden) }
        compose.runOnIdle { scene.value = scene.value.copy(reselectionCount = 3) }
        compose.runOnIdle { scene.value = scene.value.copy(visibilityState = TabVisibilityState.Visible, selectionCount = 2) }
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle {
            assertThat(events).containsExactly(TabLifecycleEvent.Selected, TabLifecycleEvent.Reselected,
                TabLifecycleEvent.Hidden, TabLifecycleEvent.Restored, TabLifecycleEvent.Hidden).inOrder()
        }
    }

    @Test fun `initially hidden scene does not start work and hidden disposal is silent`() {
        scene.value = scene(visibility = TabVisibilityState.Hidden, selections = 0)
        bind()
        compose.runOnIdle { assertThat(events).isEmpty(); mounted.value = false }
        compose.runOnIdle { assertThat(events).isEmpty(); scene.value = scene(); mounted.value = true }
        compose.runOnIdle { scene.value = scene.value.copy(visibilityState = TabVisibilityState.Hidden) }
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle { assertThat(events).containsExactly(TabLifecycleEvent.Selected, TabLifecycleEvent.Hidden).inOrder() }
    }

    @Test fun `host inactivity pauses the selected scene and restores without a tab selection change`() {
        bind()
        compose.runOnIdle { active.value = false }
        compose.runOnIdle { active.value = true }
        compose.runOnIdle { scene.value = scene.value.copy(reselectionCount = 1) }
        compose.runOnIdle {
            assertThat(events).containsExactly(TabLifecycleEvent.Selected, TabLifecycleEvent.Hidden,
                TabLifecycleEvent.Restored, TabLifecycleEvent.Reselected).inOrder()
        }
    }

    @Test fun `remount starts from current counts and reducer replacement cleans up the old binding`() {
        scene.value = scene(selections = 2).copy(reselectionCount = 4)
        val current = mutableStateOf(reducer)
        val replacementEvents = mutableListOf<TabLifecycleEvent>()
        val replacement = VisibilityReducer().apply { testBind(0, postedActions = replacementEvents) }
        compose.setContent { BindTabLifecycle(scene.value, current.value) }
        compose.runOnIdle { current.value = replacement }
        compose.runOnIdle {
            assertThat(events).containsExactly(TabLifecycleEvent.Restored, TabLifecycleEvent.Hidden).inOrder()
            assertThat(replacementEvents).containsExactly(TabLifecycleEvent.Restored)
        }
    }

    private fun bind() { compose.setContent { if (mounted.value) BindTabLifecycle(scene.value, reducer, isActive = active.value) } }
    private fun scene(visibility: TabVisibilityState = TabVisibilityState.Visible, selections: Int = 1) =
        TabSceneState(TestTab.Home, TabRoute.Root.child(TestTab.Home), visibility, selections, 0)

    private enum class TestTab(override val stableKey: String) : TabId { Home("home") }
    private class VisibilityReducer : TabChildReducer<Int, TabLifecycleEvent, Nothing>() {
        override fun onLoadAction(): TabLifecycleEvent? = null
        override suspend fun process(action: TabLifecycleEvent) = Unit
        override fun onTabSelected(context: TabLifecycleContext) = TabLifecycleEvent.Selected
        override fun onTabRestored(context: TabLifecycleContext) = TabLifecycleEvent.Restored
        override fun onTabReselected(context: TabLifecycleContext) = TabLifecycleEvent.Reselected
        override fun onTabHidden(context: TabLifecycleContext) = TabLifecycleEvent.Hidden
    }
}
