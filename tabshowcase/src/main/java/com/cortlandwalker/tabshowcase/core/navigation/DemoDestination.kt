package com.cortlandwalker.tabshowcase.core.navigation

import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.data.models.NotificationCard

sealed interface DemoDestination {
    data class Clip(
        val clip: ClipCard,
        val originLabel: String
    ) : DemoDestination
    data class Creator(val handle: String) : DemoDestination
    data class Notification(val notification: NotificationCard) : DemoDestination
    data object Settings : DemoDestination
}
