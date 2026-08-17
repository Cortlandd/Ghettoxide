package com.cortlandwalker.tabshowcase.core.navigation

import com.cortlandwalker.ghettoxide.Reducer

data class ShowcaseDestinationEntry(
    val id: Long,
    val destination: DemoDestination
)

data class ShowcaseShellState(
    val nextEntryId: Long = 1L,
    val backstack: List<ShowcaseDestinationEntry> = emptyList()
) {
    val currentEntry: ShowcaseDestinationEntry?
        get() = backstack.lastOrNull()

    val isShowingDestination: Boolean
        get() = currentEntry != null
}

sealed interface ShowcaseShellAction {
    data class NavigateTo(val destination: DemoDestination) : ShowcaseShellAction
    data object Back : ShowcaseShellAction
}

class ShowcaseShellReducer : Reducer<ShowcaseShellState, ShowcaseShellAction, Nothing>() {

    override fun onLoadAction(): ShowcaseShellAction? = null

    override suspend fun process(action: ShowcaseShellAction) {
        when (action) {
            ShowcaseShellAction.Back -> {
                if (currentState.backstack.isNotEmpty()) {
                    state { it.copy(backstack = it.backstack.dropLast(1)) }
                }
            }

            is ShowcaseShellAction.NavigateTo -> {
                state {
                    it.copy(
                        nextEntryId = it.nextEntryId + 1,
                        backstack = it.backstack + ShowcaseDestinationEntry(
                            id = it.nextEntryId,
                            destination = action.destination
                        )
                    )
                }
            }
        }
    }
}
