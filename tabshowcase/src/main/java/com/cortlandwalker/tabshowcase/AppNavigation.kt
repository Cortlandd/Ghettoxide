package com.cortlandwalker.tabshowcase

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cortlandwalker.ghettoxide.StoreContent
import com.cortlandwalker.ghettoxide.TabHost
import com.cortlandwalker.ghettoxide.rememberStoreViewModel
import com.cortlandwalker.ghettoxide.rememberTabHostController
import com.cortlandwalker.tabshowcase.core.navigation.AppTab
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.core.navigation.ShowcaseDestinationEntry
import com.cortlandwalker.tabshowcase.core.navigation.ShowcaseShellAction
import com.cortlandwalker.tabshowcase.core.navigation.ShowcaseShellReducer
import com.cortlandwalker.tabshowcase.core.navigation.ShowcaseShellState
import com.cortlandwalker.tabshowcase.features.detail.ClipDetailScreen
import com.cortlandwalker.tabshowcase.features.detail.PlaceholderDetailScreen
import com.cortlandwalker.tabshowcase.features.home.homeTabSpec
import com.cortlandwalker.tabshowcase.features.notifications.notificationsTabSpec
import com.cortlandwalker.tabshowcase.features.profile.profileTabSpec
import com.cortlandwalker.tabshowcase.features.search.searchTabSpec

@Composable
fun AppNavigation() {
    val appTabs = rememberTabHostController(
        key = "app-shell-tabs",
        initialTabId = AppTab.Home
    )
    val (shellStore, _) = rememberStoreViewModel(
        key = "showcase-shell",
        initialState = ShowcaseShellState(),
        reducerFactory = { ShowcaseShellReducer() }
    )
    val onNavigate = remember(shellStore) {
        { destination: DemoDestination ->
            shellStore.postAction(ShowcaseShellAction.NavigateTo(destination))
        }
    }
    val tabs = remember(onNavigate) {
        listOf(
            homeTabSpec(onNavigate = onNavigate),
            searchTabSpec(onNavigate = onNavigate),
            notificationsTabSpec(onNavigate = onNavigate),
            profileTabSpec(onNavigate = onNavigate)
        )
    }

    StoreContent(store = shellStore) { shellState, dispatch ->
        BackHandler(enabled = shellState.isShowingDestination || appTabs.canGoBack) {
            if (shellState.isShowingDestination) {
                dispatch(ShowcaseShellAction.Back)
            } else {
                appTabs.goBack()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (!shellState.isShowingDestination) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab.id == appTabs.selectedTabId,
                                onClick = { appTabs.selectTab(tab.id) },
                                icon = {
                                    val icon = when (tab.id) {
                                        AppTab.Home -> if (tab.id == appTabs.selectedTabId) {
                                            Icons.Rounded.Home
                                        } else {
                                            Icons.Outlined.Home
                                        }

                                        AppTab.Search -> if (tab.id == appTabs.selectedTabId) {
                                            Icons.Rounded.Search
                                        } else {
                                            Icons.Outlined.Search
                                        }

                                        AppTab.Notifications -> if (tab.id == appTabs.selectedTabId) {
                                            Icons.Rounded.Notifications
                                        } else {
                                            Icons.Outlined.Notifications
                                        }

                                        AppTab.Profile -> if (tab.id == appTabs.selectedTabId) {
                                            Icons.Rounded.Person
                                        } else {
                                            Icons.Outlined.Person
                                        }
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            val currentEntry = shellState.currentEntry
            if (currentEntry == null) {
                TabHost(
                    controller = appTabs,
                    tabs = tabs,
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                DestinationScreen(
                    entry = currentEntry,
                    modifier = Modifier.padding(paddingValues),
                    onBack = { dispatch(ShowcaseShellAction.Back) },
                    onNavigate = onNavigate
                )
            }
        }
    }
}

@Composable
private fun DestinationScreen(
    entry: ShowcaseDestinationEntry,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigate: (DemoDestination) -> Unit
) {
    key(entry.id) {
        when (val destination = entry.destination) {
            is DemoDestination.Clip -> {
                ClipDetailScreen(
                    clip = destination.clip,
                    originLabel = destination.originLabel,
                    modifier = modifier,
                    onBack = onBack,
                    onOpenCreator = {
                        onNavigate(DemoDestination.Creator(destination.clip.creator))
                    }
                )
            }

            is DemoDestination.Creator -> {
                PlaceholderDetailScreen(
                    title = "Creator Detail",
                    message = "Opened ${destination.handle} from a reducer effect. In a production app, keep the full object or stable id in the shell route and let the screen load its own heavy data.",
                    modifier = modifier,
                    onBack = onBack
                )
            }

            is DemoDestination.Notification -> {
                PlaceholderDetailScreen(
                    title = destination.notification.title,
                    message = destination.notification.message,
                    modifier = modifier,
                    onBack = onBack
                )
            }

            DemoDestination.Settings -> {
                PlaceholderDetailScreen(
                    title = "Settings",
                    message = "Profile emitted a typed settings effect, and the shell handled it without storing Android navigation objects in reducer state.",
                    modifier = modifier,
                    onBack = onBack
                )
            }
        }
    }
}
