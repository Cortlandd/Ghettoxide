package com.cortlandwalker.tabshowcase.features.notifications

import com.cortlandwalker.ghettoxide.TabSpec
import com.cortlandwalker.ghettoxide.reducerTabSpec
import com.cortlandwalker.tabshowcase.core.navigation.AppTab
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.data.demo.DemoRepositories
import com.cortlandwalker.tabshowcase.data.models.ListPosition
import com.cortlandwalker.tabshowcase.data.models.NotificationCard

data class NotificationsState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val items: List<NotificationCard> = emptyList(),
    val livePulseCount: Int = 0,
    val liveUpdatesActive: Boolean = false,
    val listPosition: ListPosition = ListPosition()
)

sealed interface NotificationsAction {
    data object Load : NotificationsAction
    data object Retry : NotificationsAction
    data object TabVisible : NotificationsAction
    data object TabHidden : NotificationsAction
    data object TabReselected : NotificationsAction
    data class SaveScroll(val position: ListPosition) : NotificationsAction
    data class OpenNotification(val notification: NotificationCard) : NotificationsAction
}

sealed interface NotificationsEffect {
    data class NavigateToNotification(val notification: NotificationCard) : NotificationsEffect
    data object ScrollToTop : NotificationsEffect
}

fun notificationsTabSpec(
    onNavigate: (DemoDestination) -> Unit
): TabSpec<AppTab> = reducerTabSpec(
    id = AppTab.Notifications,
    title = AppTab.Notifications.label,
    initialState = NotificationsState(),
    reducerFactory = { NotificationsReducer(DemoRepositories.notifications) },
    onEffect = { effect ->
        if (effect is NotificationsEffect.NavigateToNotification) {
            onNavigate(DemoDestination.Notification(effect.notification))
        }
    }
) { _, state, effects, reducer ->
    NotificationsScreen(
        state = state,
        effects = effects,
        onAction = reducer::postAction
    )
}
