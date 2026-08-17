package com.cortlandwalker.tabshowcase.features.profile

import com.cortlandwalker.ghettoxide.TabSpec
import com.cortlandwalker.ghettoxide.reducerTabSpec
import com.cortlandwalker.tabshowcase.core.navigation.AppTab
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.data.demo.DemoRepositories
import com.cortlandwalker.tabshowcase.data.models.CreatorProfile

data class ProfileState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val profile: CreatorProfile? = null
)

sealed interface ProfileAction {
    data object Load : ProfileAction
    data object Retry : ProfileAction
    data object SettingsTapped : ProfileAction
}

sealed interface ProfileEffect {
    data object OpenSettings : ProfileEffect
}

fun profileTabSpec(
    onNavigate: (DemoDestination) -> Unit
): TabSpec<AppTab> = reducerTabSpec(
    id = AppTab.Profile,
    title = AppTab.Profile.label,
    initialState = ProfileState(),
    reducerFactory = { ProfileReducer(DemoRepositories.profile) },
    onEffect = { effect ->
        if (effect is ProfileEffect.OpenSettings) {
            onNavigate(DemoDestination.Settings)
        }
    }
) { sceneState, state, _, reducer ->
    ProfileScreen(
        sceneState = sceneState,
        state = state,
        onNavigate = onNavigate,
        onAction = reducer::postAction
    )
}
