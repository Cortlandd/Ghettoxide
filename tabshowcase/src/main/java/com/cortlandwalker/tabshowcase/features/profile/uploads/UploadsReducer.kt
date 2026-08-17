package com.cortlandwalker.tabshowcase.features.profile.uploads

import com.cortlandwalker.ghettoxide.TabChildReducer
import com.cortlandwalker.ghettoxide.TabLifecycleContext
import com.cortlandwalker.tabshowcase.data.demo.FakeProfileRepository
import com.cortlandwalker.tabshowcase.data.models.ListPosition

class UploadsReducer(
    private val repository: FakeProfileRepository
) : TabChildReducer<UploadsState, UploadsAction, UploadsEffect>() {

    override fun onLoadAction(): UploadsAction? = null

    override fun onTabSelected(context: TabLifecycleContext): UploadsAction? {
        return if (currentState.clips.isEmpty()) UploadsAction.Load else null
    }

    override fun onTabRestored(context: TabLifecycleContext): UploadsAction? {
        return if (currentState.clips.isEmpty()) UploadsAction.Load else null
    }

    override fun onTabReselected(context: TabLifecycleContext): UploadsAction? =
        UploadsAction.TabReselected

    override suspend fun process(action: UploadsAction) {
        when (action) {
            UploadsAction.Load,
            UploadsAction.Retry -> loadUploads(reset = true, filter = currentState.filter)

            UploadsAction.LoadMore -> {
                if (!currentState.isPaging && currentState.nextCursor != null) {
                    loadUploads(reset = false, filter = currentState.filter)
                }
            }

            UploadsAction.TabReselected -> emit(UploadsEffect.ScrollToTop)

            is UploadsAction.ChangeFilter -> {
                state {
                    it.copy(
                        filter = action.filter,
                        clips = emptyList(),
                        nextCursor = null,
                        listPosition = ListPosition()
                    )
                }
                loadUploads(reset = true, filter = action.filter)
            }

            is UploadsAction.SaveScroll -> state { it.copy(listPosition = action.position) }
            is UploadsAction.OpenClip -> emit(UploadsEffect.NavigateToClip(action.clip))
        }
    }

    private suspend fun loadUploads(
        reset: Boolean,
        filter: com.cortlandwalker.tabshowcase.data.models.UploadFilter
    ) {
        if (reset) {
            state { it.copy(isLoading = true, errorMessage = null, filter = filter) }
        } else {
            state { it.copy(isPaging = true, errorMessage = null) }
        }

        runCatching {
            repository.loadUploads(
                filter = filter,
                cursor = if (reset) null else currentState.nextCursor
            )
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
                    errorMessage = error.message ?: "Uploads failed."
                )
            }
        }
    }
}
