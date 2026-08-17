package com.cortlandwalker.tabshowcase.features.profile.collections

import com.cortlandwalker.ghettoxide.TabSpec
import com.cortlandwalker.ghettoxide.reducerTabSpec
import com.cortlandwalker.tabshowcase.core.navigation.ProfileTab
import com.cortlandwalker.tabshowcase.data.demo.DemoRepositories
import com.cortlandwalker.tabshowcase.data.models.CollectionCard

data class CollectionsState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val collections: List<CollectionCard> = emptyList()
)

sealed interface CollectionsAction {
    data object Load : CollectionsAction
    data object Retry : CollectionsAction
}

fun collectionsTabSpec(): TabSpec<ProfileTab> = reducerTabSpec(
    id = ProfileTab.Collections,
    title = ProfileTab.Collections.label,
    initialState = CollectionsState(),
    reducerFactory = { CollectionsReducer(DemoRepositories.profile) }
) { _, state, _, reducer ->
    CollectionsScreen(
        state = state,
        onAction = reducer::postAction
    )
}
