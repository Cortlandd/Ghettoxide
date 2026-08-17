package com.cortlandwalker.tabshowcase.features.profile.liked

import com.cortlandwalker.ghettoxide.TabSpec
import com.cortlandwalker.ghettoxide.reducerTabSpec
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.core.navigation.ProfileTab
import com.cortlandwalker.tabshowcase.data.demo.DemoRepositories
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.data.models.ListPosition

data class LikedState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val clips: List<ClipCard> = emptyList(),
    val nextCursor: Int? = null,
    val isPaging: Boolean = false,
    val listPosition: ListPosition = ListPosition()
)

sealed interface LikedAction {
    data object Load : LikedAction
    data object Retry : LikedAction
    data object LoadMore : LikedAction
    data object TabReselected : LikedAction
    data class SaveScroll(val position: ListPosition) : LikedAction
    data class OpenClip(val clip: ClipCard) : LikedAction
}

sealed interface LikedEffect {
    data class NavigateToClip(val clip: ClipCard) : LikedEffect
    data object ScrollToTop : LikedEffect
}

fun likedTabSpec(
    onNavigate: (DemoDestination) -> Unit
): TabSpec<ProfileTab> = reducerTabSpec(
    id = ProfileTab.Liked,
    title = ProfileTab.Liked.label,
    initialState = LikedState(),
    reducerFactory = { LikedReducer(DemoRepositories.profile) },
    onEffect = { effect ->
        if (effect is LikedEffect.NavigateToClip) {
            onNavigate(
                DemoDestination.Clip(
                    clip = effect.clip,
                    originLabel = "Profile / Liked"
                )
            )
        }
    }
) { _, state, effects, reducer ->
    LikedScreen(
        state = state,
        effects = effects,
        onAction = reducer::postAction
    )
}
