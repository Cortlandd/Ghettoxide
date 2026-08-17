package com.cortlandwalker.ghettoxide

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private enum class DemoTab(override val stableKey: String) : TabId {
    Home("home"),
    Search("search"),
    Profile("profile")
}

class TabHostReducerTest {

    private lateinit var reducer: TabHostReducer<DemoTab>

    @Before
    fun setUp() {
        reducer = TabHostReducer(DemoTab.Home)
        reducer.testBind(
            initialState = TabHostState(selectedTabId = DemoTab.Home)
        )
    }

    @Test
    fun `selecting a new tab updates selection count visibility and backstack`() = runTest {
        reducer.accept(TabHostAction.SelectTab(DemoTab.Search))

        val state = reducer.currentState
        assertThat(state.selectedTabId).isEqualTo(DemoTab.Search)
        assertThat(state.selectionCount(DemoTab.Home)).isEqualTo(1)
        assertThat(state.selectionCount(DemoTab.Search)).isEqualTo(1)
        assertThat(state.visibility(DemoTab.Home)).isEqualTo(TabVisibilityState.Hidden)
        assertThat(state.visibility(DemoTab.Search)).isEqualTo(TabVisibilityState.Visible)
        assertThat(state.backstack).containsExactly(DemoTab.Home, DemoTab.Search).inOrder()
    }

    @Test
    fun `reselecting the current tab increments reselection count`() = runTest {
        reducer.accept(TabHostAction.SelectTab(DemoTab.Search))
        reducer.accept(TabHostAction.SelectTab(DemoTab.Search))

        val state = reducer.currentState
        assertThat(state.selectedTabId).isEqualTo(DemoTab.Search)
        assertThat(state.reselectionCount(DemoTab.Search)).isEqualTo(1)
        assertThat(state.backstack).containsExactly(DemoTab.Home, DemoTab.Search).inOrder()
    }

    @Test
    fun `back selects the previous tab and preserves restore counts`() = runTest {
        reducer.accept(TabHostAction.SelectTab(DemoTab.Search))
        reducer.accept(TabHostAction.SelectTab(DemoTab.Profile))
        reducer.accept(TabHostAction.Back())

        val state = reducer.currentState
        assertThat(state.selectedTabId).isEqualTo(DemoTab.Search)
        assertThat(state.selectionCount(DemoTab.Search)).isEqualTo(2)
        assertThat(state.backstack).containsExactly(DemoTab.Home, DemoTab.Search).inOrder()
        assertThat(state.visibility(DemoTab.Profile)).isEqualTo(TabVisibilityState.Hidden)
        assertThat(state.visibility(DemoTab.Search)).isEqualTo(TabVisibilityState.Visible)
    }
}
