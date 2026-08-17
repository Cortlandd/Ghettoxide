package com.cortlandwalker.tabshowcase.core.navigation

import com.cortlandwalker.ghettoxide.TabId

enum class AppTab(
    override val stableKey: String,
    val label: String
) : TabId {
    Home("home", "Home"),
    Search("search", "Search"),
    Notifications("notifications", "Notifications"),
    Profile("profile", "Profile")
}

enum class ProfileTab(
    override val stableKey: String,
    val label: String
) : TabId {
    Uploads("uploads", "Uploads"),
    Liked("liked", "Liked"),
    Collections("collections", "Collections")
}
