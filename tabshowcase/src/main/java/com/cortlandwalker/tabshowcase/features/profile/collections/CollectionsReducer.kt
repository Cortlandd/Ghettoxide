package com.cortlandwalker.tabshowcase.features.profile.collections

import com.cortlandwalker.ghettoxide.TabChildReducer
import com.cortlandwalker.ghettoxide.TabLifecycleContext
import com.cortlandwalker.tabshowcase.data.demo.FakeProfileRepository

class CollectionsReducer(
    private val repository: FakeProfileRepository
) : TabChildReducer<CollectionsState, CollectionsAction, Nothing>() {

    override fun onLoadAction(): CollectionsAction? = null

    override fun onTabSelected(context: TabLifecycleContext): CollectionsAction? {
        return if (currentState.collections.isEmpty()) CollectionsAction.Load else null
    }

    override fun onTabRestored(context: TabLifecycleContext): CollectionsAction? {
        return if (currentState.collections.isEmpty()) CollectionsAction.Load else null
    }

    override suspend fun process(action: CollectionsAction) {
        when (action) {
            CollectionsAction.Load,
            CollectionsAction.Retry -> loadCollections()
        }
    }

    private suspend fun loadCollections() {
        state { it.copy(isLoading = true, errorMessage = null) }

        runCatching {
            repository.loadCollections()
        }.onSuccess { collections ->
            state {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    collections = collections
                )
            }
        }.onFailure { error ->
            state {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Collections failed."
                )
            }
        }
    }
}
