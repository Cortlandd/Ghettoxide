package com.cortlandwalker.tabshowcase.features.home

import com.cortlandwalker.ghettoxide.TabSpec
import com.cortlandwalker.ghettoxide.reducerTabSpec
import com.cortlandwalker.tabshowcase.core.navigation.AppTab
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.data.demo.DemoRepositories
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.data.models.ListPosition

data class HomeState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val clips: List<ClipCard> = emptyList(),
    val nextCursor: Int? = null,
    val isPaging: Boolean = false,
    val listPosition: ListPosition = ListPosition()
)

sealed interface HomeAction {
    data object Load : HomeAction
    data object Retry : HomeAction
    data object LoadMore : HomeAction
    data object TabReselected : HomeAction
    data class SaveScroll(val position: ListPosition) : HomeAction
    data class OpenClip(val clip: ClipCard) : HomeAction
}

sealed interface HomeEffect {
    data class NavigateToClip(val clip: ClipCard) : HomeEffect
    data object ScrollToTop : HomeEffect
}

fun homeTabSpec(
    onNavigate: (DemoDestination) -> Unit
): TabSpec<AppTab> = reducerTabSpec(
    id = AppTab.Home,
    title = AppTab.Home.label,
    initialState = HomeState(),
    reducerFactory = { HomeReducer(DemoRepositories.home) },
    onEffect = { effect ->
        if (effect is HomeEffect.NavigateToClip) {
            onNavigate(
                DemoDestination.Clip(
                    clip = effect.clip,
                    originLabel = "Home"
                )
            )
        }
    }
) { scene, state, effects, reducer ->
    HomeScreen(
        state = state,
        effects = effects,
        isVisible = scene.visibilityState == com.cortlandwalker.ghettoxide.TabVisibilityState.Visible,
        onAction = reducer::postAction
    )
}
