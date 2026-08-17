package com.cortlandwalker.tabshowcase.features.profile.uploads

import com.cortlandwalker.ghettoxide.TabSpec
import com.cortlandwalker.ghettoxide.reducerTabSpec
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.core.navigation.ProfileTab
import com.cortlandwalker.tabshowcase.data.demo.DemoRepositories
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.data.models.ListPosition
import com.cortlandwalker.tabshowcase.data.models.UploadFilter

data class UploadsState(
    val filter: UploadFilter = UploadFilter.All,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val clips: List<ClipCard> = emptyList(),
    val nextCursor: Int? = null,
    val isPaging: Boolean = false,
    val listPosition: ListPosition = ListPosition()
)

sealed interface UploadsAction {
    data object Load : UploadsAction
    data object Retry : UploadsAction
    data object LoadMore : UploadsAction
    data object TabReselected : UploadsAction
    data class ChangeFilter(val filter: UploadFilter) : UploadsAction
    data class SaveScroll(val position: ListPosition) : UploadsAction
    data class OpenClip(val clip: ClipCard) : UploadsAction
}

sealed interface UploadsEffect {
    data class NavigateToClip(val clip: ClipCard) : UploadsEffect
    data object ScrollToTop : UploadsEffect
}

fun uploadsTabSpec(
    onNavigate: (DemoDestination) -> Unit
): TabSpec<ProfileTab> = reducerTabSpec(
    id = ProfileTab.Uploads,
    title = ProfileTab.Uploads.label,
    initialState = UploadsState(),
    reducerFactory = { UploadsReducer(DemoRepositories.profile) },
    onEffect = { effect ->
        if (effect is UploadsEffect.NavigateToClip) {
            onNavigate(
                DemoDestination.Clip(
                    clip = effect.clip,
                    originLabel = "Profile / Uploads"
                )
            )
        }
    }
) { _, state, effects, reducer ->
    UploadsScreen(
        state = state,
        effects = effects,
        onAction = reducer::postAction
    )
}
