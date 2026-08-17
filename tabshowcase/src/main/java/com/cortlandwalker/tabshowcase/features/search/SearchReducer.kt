package com.cortlandwalker.tabshowcase.features.search

import com.cortlandwalker.ghettoxide.TabChildReducer
import com.cortlandwalker.ghettoxide.TabLifecycleContext
import com.cortlandwalker.tabshowcase.data.demo.FakeSearchRepository

class SearchReducer(
    private val repository: FakeSearchRepository
) : TabChildReducer<SearchState, SearchAction, SearchEffect>() {

    override fun onLoadAction(): SearchAction? = null

    override fun onTabReselected(context: TabLifecycleContext): SearchAction? =
        SearchAction.TabReselected

    override suspend fun process(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> state { it.copy(query = action.value) }

            SearchAction.SubmitSearch -> {
                val trimmed = currentState.query.trim()
                if (trimmed.isEmpty()) {
                    state {
                        it.copy(
                            submittedQuery = "",
                            isLoading = false,
                            errorMessage = null,
                            results = emptyList(),
                            nextCursor = null
                        )
                    }
                } else {
                    loadSearch(reset = true, query = trimmed)
                }
            }

            SearchAction.Retry -> {
                if (currentState.submittedQuery.isNotEmpty()) {
                    loadSearch(reset = true, query = currentState.submittedQuery)
                }
            }

            SearchAction.LoadMore -> {
                val nextCursor = currentState.nextCursor
                if (!currentState.isPaging && nextCursor != null && currentState.submittedQuery.isNotEmpty()) {
                    loadSearch(
                        reset = false,
                        query = currentState.submittedQuery
                    )
                }
            }

            SearchAction.TabReselected -> emit(SearchEffect.ScrollToTop)
            is SearchAction.SaveScroll -> state { it.copy(listPosition = action.position) }
            is SearchAction.OpenCreator -> emit(SearchEffect.NavigateToCreator(action.clip.creator))
        }
    }

    private suspend fun loadSearch(
        reset: Boolean,
        query: String
    ) {
        if (reset) {
            state {
                it.copy(
                    submittedQuery = query,
                    isLoading = true,
                    errorMessage = null
                )
            }
        } else {
            state { it.copy(isPaging = true, errorMessage = null) }
        }

        runCatching {
            repository.search(
                query = query,
                cursor = if (reset) null else currentState.nextCursor
            )
        }.onSuccess { page ->
            state {
                it.copy(
                    submittedQuery = query,
                    isLoading = false,
                    errorMessage = null,
                    results = if (reset) page.items else it.results + page.items,
                    nextCursor = page.nextCursor,
                    isPaging = false
                )
            }
        }.onFailure { error ->
            state {
                it.copy(
                    submittedQuery = query,
                    isLoading = false,
                    isPaging = false,
                    errorMessage = error.message ?: "Search failed."
                )
            }
        }
    }
}
