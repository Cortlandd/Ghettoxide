package com.cortlandwalker.tabshowcase.features.search

import com.cortlandwalker.ghettoxide.TabSpec
import com.cortlandwalker.ghettoxide.reducerTabSpec
import com.cortlandwalker.tabshowcase.core.navigation.AppTab
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.data.demo.DemoRepositories
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.data.models.ListPosition

data class SearchState(
    val query: String = "",
    val submittedQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val results: List<ClipCard> = emptyList(),
    val nextCursor: Int? = null,
    val isPaging: Boolean = false,
    val listPosition: ListPosition = ListPosition()
)

sealed interface SearchAction {
    data class QueryChanged(val value: String) : SearchAction
    data object SubmitSearch : SearchAction
    data object Retry : SearchAction
    data object LoadMore : SearchAction
    data object TabReselected : SearchAction
    data class SaveScroll(val position: ListPosition) : SearchAction
    data class OpenCreator(val clip: ClipCard) : SearchAction
}

sealed interface SearchEffect {
    data class NavigateToCreator(val creatorHandle: String) : SearchEffect
    data object ScrollToTop : SearchEffect
}

fun searchTabSpec(
    onNavigate: (DemoDestination) -> Unit
): TabSpec<AppTab> = reducerTabSpec(
    id = AppTab.Search,
    title = AppTab.Search.label,
    initialState = SearchState(),
    reducerFactory = { SearchReducer(DemoRepositories.search) },
    onEffect = { effect ->
        if (effect is SearchEffect.NavigateToCreator) {
            onNavigate(DemoDestination.Creator(effect.creatorHandle))
        }
    }
) { _, state, effects, reducer ->
    SearchScreen(
        state = state,
        effects = effects,
        onAction = reducer::postAction
    )
}
