package com.cortlandwalker.tabshowcase.features.home

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
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.ui.components.ClipCardRow
import com.cortlandwalker.tabshowcase.ui.components.ErrorPane
import com.cortlandwalker.tabshowcase.ui.components.LoadMoreFooter
import com.cortlandwalker.tabshowcase.ui.components.LoadingPane
import com.cortlandwalker.tabshowcase.ui.components.rememberTrackedLazyListState
import kotlinx.coroutines.flow.Flow

@Composable
fun HomeScreen(
    state: HomeState,
    effects: Flow<HomeEffect>,
    onAction: (HomeAction) -> Unit
) {
    val listState = rememberTrackedLazyListState(
        position = state.listPosition,
        onPositionChanged = { onAction(HomeAction.SaveScroll(it)) }
    )

    ObserveEffects(effects) { effect ->
        if (effect is HomeEffect.ScrollToTop) {
            listState.animateScrollToItem(0)
        }
    }

    when {
        state.isLoading && state.clips.isEmpty() -> {
            LoadingPane(
                title = "Loading Home",
                subtitle = "Pulling together a creator-heavy feed."
            )
        }

        state.errorMessage != null && state.clips.isEmpty() -> {
            ErrorPane(
                title = "Home stalled out",
                subtitle = state.errorMessage,
                retryLabel = "Retry",
                onRetry = { onAction(HomeAction.Retry) }
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
                            text = "Home",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Reducer-backed bottom tabs with preserved list state and explicit reselect behavior.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(
                    items = state.clips,
                    key = ClipCard::id
                ) { clip ->
                    ClipCardRow(
                        clip = clip,
                        onOpen = { onAction(HomeAction.OpenClip(clip)) }
                    )
                }

                if (state.nextCursor != null) {
                    item {
                        LoadMoreFooter(
                            loading = state.isPaging,
                            onLoadMore = { onAction(HomeAction.LoadMore) }
                        )
                    }
                }
            }
        }
    }
}
