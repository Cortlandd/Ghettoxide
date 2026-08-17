package com.cortlandwalker.tabshowcase.features.home

import com.cortlandwalker.ghettoxide.TabChildReducer
import com.cortlandwalker.ghettoxide.TabLifecycleContext
import com.cortlandwalker.tabshowcase.data.demo.FakeHomeRepository

class HomeReducer(
    private val repository: FakeHomeRepository
) : TabChildReducer<HomeState, HomeAction, HomeEffect>() {

    override fun onLoadAction(): HomeAction? = null

    override fun onTabSelected(context: TabLifecycleContext): HomeAction? {
        return if (currentState.clips.isEmpty()) HomeAction.Load else null
    }

    override fun onTabReselected(context: TabLifecycleContext): HomeAction? = HomeAction.TabReselected

    override suspend fun process(action: HomeAction) {
        when (action) {
            HomeAction.Load,
            HomeAction.Retry -> loadPage(reset = true)

            HomeAction.LoadMore -> {
                if (!currentState.isPaging && currentState.nextCursor != null) {
                    loadPage(reset = false)
                }
            }

            HomeAction.TabReselected -> emit(HomeEffect.ScrollToTop)
            is HomeAction.OpenClip -> emit(HomeEffect.NavigateToClip(action.clip))
            is HomeAction.SaveScroll -> state { it.copy(listPosition = action.position) }
        }
    }

    private suspend fun loadPage(reset: Boolean) {
        if (reset) {
            state { it.copy(isLoading = true, errorMessage = null) }
        } else {
            state { it.copy(isPaging = true, errorMessage = null) }
        }

        runCatching {
            repository.loadFeed(cursor = if (reset) null else currentState.nextCursor)
        }.onSuccess { page ->
            state {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    clips = if (reset) page.items else it.clips + page.items,
                    nextCursor = page.nextCursor,
                    isPaging = false
                )
            }
        }.onFailure { error ->
            state {
                it.copy(
                    isLoading = false,
                    isPaging = false,
                    errorMessage = error.message ?: "Home feed failed."
                )
            }
        }
    }
}
