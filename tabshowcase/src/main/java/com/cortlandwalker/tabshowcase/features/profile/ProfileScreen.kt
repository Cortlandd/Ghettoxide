package com.cortlandwalker.tabshowcase.features.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cortlandwalker.ghettoxide.TabHost
import com.cortlandwalker.ghettoxide.TabSceneState
import com.cortlandwalker.ghettoxide.rememberTabHostController
import com.cortlandwalker.tabshowcase.core.navigation.AppTab
import com.cortlandwalker.tabshowcase.core.navigation.DemoDestination
import com.cortlandwalker.tabshowcase.core.navigation.ProfileTab
import com.cortlandwalker.tabshowcase.features.profile.collections.collectionsTabSpec
import com.cortlandwalker.tabshowcase.features.profile.liked.likedTabSpec
import com.cortlandwalker.tabshowcase.features.profile.uploads.uploadsTabSpec
import com.cortlandwalker.tabshowcase.ui.components.ErrorPane
import com.cortlandwalker.tabshowcase.ui.components.LoadingPane
import com.cortlandwalker.tabshowcase.ui.components.StatPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sceneState: TabSceneState<AppTab>,
    state: ProfileState,
    onNavigate: (DemoDestination) -> Unit,
    onAction: (ProfileAction) -> Unit
) {
    when {
        state.isLoading && state.profile == null -> {
            LoadingPane(
                title = "Loading Profile",
                subtitle = "Hydrating a nested tab shell inside the parent tab."
            )
        }

        state.errorMessage != null && state.profile == null -> {
            ErrorPane(
                title = "Profile failed",
                subtitle = state.errorMessage,
                retryLabel = "Retry",
                onRetry = { onAction(ProfileAction.Retry) }
            )
        }

        state.profile != null -> {
            val nestedTabsController = rememberTabHostController(
                key = "profile-nested-tabs",
                initialTabId = ProfileTab.Uploads
            )
            val nestedTabs = remember(onNavigate) {
                listOf(
                    uploadsTabSpec(onNavigate),
                    likedTabSpec(onNavigate),
                    collectionsTabSpec()
                )
            }

            BackHandler(enabled = nestedTabsController.canGoBack) {
                nestedTabsController.goBack()
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = state.profile.displayName,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.profile.handle} • ${state.profile.followersLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.profile.bio,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill(label = "Nested tabs")
                        StatPill(label = "Saved scroll")
                        OutlinedButton(onClick = { onAction(ProfileAction.SettingsTapped) }) {
                            Text("Settings")
                        }
                    }
                }

                SecondaryTabRow(
                    selectedTabIndex = nestedTabs.indexOfFirst { it.id == nestedTabsController.selectedTabId }
                        .coerceAtLeast(0)
                ) {
                    nestedTabs.forEach { tab ->
                        Tab(
                            selected = tab.id == nestedTabsController.selectedTabId,
                            onClick = { nestedTabsController.selectTab(tab.id) },
                            text = { Text(tab.title) }
                        )
                    }
                }

                TabHost(
                    controller = nestedTabsController,
                    tabs = nestedTabs,
                    modifier = Modifier.fillMaxSize(),
                    route = sceneState.route
                )
            }
        }
    }
}
