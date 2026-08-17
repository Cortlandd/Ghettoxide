package com.cortlandwalker.tabshowcase.features.notifications

import com.cortlandwalker.ghettoxide.TabChildReducer
import com.cortlandwalker.ghettoxide.TabLifecycleContext
import com.cortlandwalker.tabshowcase.data.demo.FakeNotificationsRepository

class NotificationsReducer(
    private val repository: FakeNotificationsRepository
) : TabChildReducer<NotificationsState, NotificationsAction, NotificationsEffect>() {

    override fun onLoadAction(): NotificationsAction? = null

    override fun onTabSelected(context: TabLifecycleContext): NotificationsAction? =
        NotificationsAction.TabVisible

    override fun onTabRestored(context: TabLifecycleContext): NotificationsAction? =
        NotificationsAction.TabVisible

    override fun onTabReselected(context: TabLifecycleContext): NotificationsAction? =
        NotificationsAction.TabReselected

    override fun onTabHidden(context: TabLifecycleContext): NotificationsAction? =
        NotificationsAction.TabHidden

    override suspend fun process(action: NotificationsAction) {
        when (action) {
            NotificationsAction.Load,
            NotificationsAction.Retry -> loadNotifications()

            NotificationsAction.TabVisible -> {
                if (currentState.items.isEmpty()) {
                    postAction(NotificationsAction.Load)
                }
                collectInScopeLatest(
                    key = LIVE_PULSE_KEY,
                    flow = repository.observeUnreadPulse(),
                    onEach = { pulse ->
                        state {
                            it.copy(
                                livePulseCount = pulse,
                                liveUpdatesActive = true
                            )
                        }
                    }
                )
            }

            NotificationsAction.TabHidden -> {
                cancelScopedJob(LIVE_PULSE_KEY)
                state { it.copy(liveUpdatesActive = false) }
            }

            NotificationsAction.TabReselected -> emit(NotificationsEffect.ScrollToTop)
            is NotificationsAction.SaveScroll -> state { it.copy(listPosition = action.position) }
            is NotificationsAction.OpenNotification -> emit(NotificationsEffect.NavigateToNotification(action.notification))
        }
    }

    private suspend fun loadNotifications() {
        state { it.copy(isLoading = true, errorMessage = null) }

        runCatching {
            repository.loadNotifications()
        }.onSuccess { notifications ->
            state {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    items = notifications
                )
            }
        }.onFailure { error ->
            state {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Notifications failed."
                )
            }
        }
    }

    private companion object {
        const val LIVE_PULSE_KEY = "notifications-live-pulse"
    }
}
