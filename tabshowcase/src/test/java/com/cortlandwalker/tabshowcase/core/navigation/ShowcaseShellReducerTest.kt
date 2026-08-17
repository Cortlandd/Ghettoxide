package com.cortlandwalker.tabshowcase.core.navigation

import com.cortlandwalker.ghettoxide.testBind
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShowcaseShellReducerTest {

    private lateinit var reducer: ShowcaseShellReducer

    @Before
    fun setUp() {
        reducer = ShowcaseShellReducer()
        reducer.testBind(initialState = ShowcaseShellState())
    }

    @Test
    fun `navigate pushes a destination entry`() = runBlocking {
        reducer.accept(
            ShowcaseShellAction.NavigateTo(
                DemoDestination.Clip(
                    clip = demoClip(),
                    originLabel = "Home"
                )
            )
        )

        val state = reducer.currentState
        assertTrue(state.isShowingDestination)
        assertEquals(1, state.backstack.size)
        assertEquals(2L, state.nextEntryId)
        val entry = requireNotNull(state.currentEntry)
        assertEquals(1L, entry.id)
        val destination = entry.destination as DemoDestination.Clip
        assertEquals("Home", destination.originLabel)
        assertEquals("clip-1", destination.clip.id)
    }

    @Test
    fun `back pops the top destination`() = runBlocking {
        reducer.accept(ShowcaseShellAction.NavigateTo(DemoDestination.Creator("@maya")))
        reducer.accept(
            ShowcaseShellAction.NavigateTo(
                DemoDestination.Clip(
                    clip = demoClip(),
                    originLabel = "Profile / Uploads"
                )
            )
        )

        reducer.accept(ShowcaseShellAction.Back)

        val state = reducer.currentState
        assertTrue(state.isShowingDestination)
        assertEquals(1, state.backstack.size)
        assertTrue(state.currentEntry?.destination is DemoDestination.Creator)

        reducer.accept(ShowcaseShellAction.Back)

        assertFalse(reducer.currentState.isShowingDestination)
        assertTrue(reducer.currentState.backstack.isEmpty())
    }

    private fun demoClip() = ClipCard(
        id = "clip-1",
        title = "Pocketable Color Grading",
        creator = "@maya",
        tag = "For You",
        duration = "0:31",
        likesLabel = "12.4K likes"
    )
}
