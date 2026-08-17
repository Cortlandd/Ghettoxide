package com.cortlandwalker.tabshowcase.features.profile.liked

import com.cortlandwalker.ghettoxide.TabChildReducer
import com.cortlandwalker.ghettoxide.TabLifecycleContext
import com.cortlandwalker.tabshowcase.data.demo.FakeProfileRepository

class LikedReducer(
    private val repository: FakeProfileRepository
) : TabChildReducer<LikedState, LikedAction, LikedEffect>() {

    override fun onLoadAction(): LikedAction? = null

    override fun onTabSelected(context: TabLifecycleContext): LikedAction? {
        return if (currentState.clips.isEmpty()) LikedAction.Load else null
    }

    override fun onTabRestored(context: TabLifecycleContext): LikedAction? {
        return if (currentState.clips.isEmpty()) LikedAction.Load else null
    }

    override fun onTabReselected(context: TabLifecycleContext): LikedAction? =
        LikedAction.TabReselected

    override suspend fun process(action: LikedAction) {
        when (action) {
            LikedAction.Load,
            LikedAction.Retry -> loadLiked(reset = true)

            LikedAction.LoadMore -> {
                if (!currentState.isPaging && currentState.nextCursor != null) {
                    loadLiked(reset = false)
                }
            }

            LikedAction.TabReselected -> emit(LikedEffect.ScrollToTop)
            is LikedAction.SaveScroll -> state { it.copy(listPosition = action.position) }
            is LikedAction.OpenClip -> emit(LikedEffect.NavigateToClip(action.clip))
        }
    }

    private suspend fun loadLiked(reset: Boolean) {
        if (reset) {
            state { it.copy(isLoading = true, errorMessage = null) }
        } else {
            state { it.copy(isPaging = true, errorMessage = null) }
        }

        runCatching {
            repository.loadLiked(cursor = if (reset) null else currentState.nextCursor)
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
                    errorMessage = error.message ?: "Liked failed."
                )
            }
        }
    }
}
