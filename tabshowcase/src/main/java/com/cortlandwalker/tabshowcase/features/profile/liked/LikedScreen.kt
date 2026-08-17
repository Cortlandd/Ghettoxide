package com.cortlandwalker.tabshowcase.features.profile.liked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun LikedScreen(
    state: LikedState,
    effects: Flow<LikedEffect>,
    onAction: (LikedAction) -> Unit
) {
    val listState = rememberTrackedLazyListState(
        position = state.listPosition,
        onPositionChanged = { onAction(LikedAction.SaveScroll(it)) }
    )

    ObserveEffects(effects) { effect ->
        if (effect is LikedEffect.ScrollToTop) {
            listState.animateScrollToItem(0)
        }
    }

    when {
        state.isLoading && state.clips.isEmpty() -> {
            LoadingPane(
                title = "Loading Liked",
                subtitle = "The first load intentionally fails once so Retry is demonstrated."
            )
        }

        state.errorMessage != null && state.clips.isEmpty() -> {
            ErrorPane(
                title = "Liked hit a deterministic error",
                subtitle = state.errorMessage,
                retryLabel = "Retry",
                onRetry = { onAction(LikedAction.Retry) }
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = state.clips,
                    key = ClipCard::id
                ) { clip ->
                    ClipCardRow(
                        clip = clip,
                        onOpen = { onAction(LikedAction.OpenClip(clip)) }
                    )
                }

                if (state.nextCursor != null) {
                    item {
                        LoadMoreFooter(
                            loading = state.isPaging,
                            onLoadMore = { onAction(LikedAction.LoadMore) }
                        )
                    }
                }
            }
        }
    }
}
