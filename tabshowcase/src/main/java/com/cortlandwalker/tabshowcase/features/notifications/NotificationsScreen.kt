package com.cortlandwalker.tabshowcase.features.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cortlandwalker.ghettoxide.ObserveEffects
import com.cortlandwalker.tabshowcase.data.models.NotificationCard
import com.cortlandwalker.tabshowcase.ui.components.ErrorPane
import com.cortlandwalker.tabshowcase.ui.components.LoadingPane
import com.cortlandwalker.tabshowcase.ui.components.NotificationRow
import com.cortlandwalker.tabshowcase.ui.components.rememberTrackedLazyListState
import kotlinx.coroutines.flow.Flow

@Composable
fun NotificationsScreen(
    state: NotificationsState,
    effects: Flow<NotificationsEffect>,
    onAction: (NotificationsAction) -> Unit
) {
    val listState = rememberTrackedLazyListState(
        position = state.listPosition,
        onPositionChanged = { onAction(NotificationsAction.SaveScroll(it)) }
    )

    ObserveEffects(effects) { effect ->
        if (effect is NotificationsEffect.ScrollToTop) {
            listState.animateScrollToItem(0)
        }
    }

    when {
        state.isLoading && state.items.isEmpty() -> {
            LoadingPane(
                title = "Loading Notifications",
                subtitle = "This tab also starts a live pulse while visible."
            )
        }

        state.errorMessage != null && state.items.isEmpty() -> {
            ErrorPane(
                title = "Notifications failed",
                subtitle = state.errorMessage,
                retryLabel = "Retry",
                onRetry = { onAction(NotificationsAction.Retry) }
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.liveUpdatesActive) {
                                "Live pulse is active: ${state.livePulseCount} unread checks cycling while the tab is visible."
                            } else {
                                "Live pulse is paused while this tab is offscreen so we do not keep extra collectors alive."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(
                    items = state.items,
                    key = NotificationCard::id
                ) { notification ->
                    NotificationRow(
                        notification = notification,
                        onOpen = { onAction(NotificationsAction.OpenNotification(notification)) }
                    )
                }
            }
        }
    }
}
