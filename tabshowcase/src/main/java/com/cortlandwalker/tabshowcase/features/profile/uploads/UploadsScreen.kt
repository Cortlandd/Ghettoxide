package com.cortlandwalker.tabshowcase.features.profile.uploads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cortlandwalker.ghettoxide.ObserveEffects
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.data.models.UploadFilter
import com.cortlandwalker.tabshowcase.ui.components.ClipCardRow
import com.cortlandwalker.tabshowcase.ui.components.ErrorPane
import com.cortlandwalker.tabshowcase.ui.components.LoadMoreFooter
import com.cortlandwalker.tabshowcase.ui.components.LoadingPane
import com.cortlandwalker.tabshowcase.ui.components.rememberTrackedLazyListState
import kotlinx.coroutines.flow.Flow

@Composable
fun UploadsScreen(
    state: UploadsState,
    effects: Flow<UploadsEffect>,
    onAction: (UploadsAction) -> Unit
) {
    val listState = rememberTrackedLazyListState(
        position = state.listPosition,
        onPositionChanged = { onAction(UploadsAction.SaveScroll(it)) }
    )

    ObserveEffects(effects) { effect ->
        if (effect is UploadsEffect.ScrollToTop) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UploadFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter == state.filter,
                        onClick = { onAction(UploadsAction.ChangeFilter(filter)) },
                        label = { Text(filter.name.lowercase().replaceFirstChar(Char::titlecase)) }
                    )
                }
            }
        }

        when {
            state.isLoading && state.clips.isEmpty() -> {
                item {
                    LoadingPane(
                        title = "Loading Uploads",
                        subtitle = "Reducer state keeps the selected filter and list position."
                    )
                }
            }

            state.errorMessage != null && state.clips.isEmpty() -> {
                item {
                    ErrorPane(
                        title = "Uploads failed",
                        subtitle = state.errorMessage,
                        retryLabel = "Retry",
                        onRetry = { onAction(UploadsAction.Retry) }
                    )
                }
            }

            else -> {
                items(
                    items = state.clips,
                    key = ClipCard::id
                ) { clip ->
                    ClipCardRow(
                        clip = clip,
                        onOpen = { onAction(UploadsAction.OpenClip(clip)) }
                    )
                }

                if (state.nextCursor != null) {
                    item {
                        LoadMoreFooter(
                            loading = state.isPaging,
                            onLoadMore = { onAction(UploadsAction.LoadMore) }
                        )
                    }
                }
            }
        }
    }
}
