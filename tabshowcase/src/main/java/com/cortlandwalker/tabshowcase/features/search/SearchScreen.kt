package com.cortlandwalker.tabshowcase.features.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cortlandwalker.ghettoxide.ObserveEffects
import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.ui.components.ClipCardRow
import com.cortlandwalker.tabshowcase.ui.components.EmptyPane
import com.cortlandwalker.tabshowcase.ui.components.ErrorPane
import com.cortlandwalker.tabshowcase.ui.components.LoadMoreFooter
import com.cortlandwalker.tabshowcase.ui.components.LoadingPane
import com.cortlandwalker.tabshowcase.ui.components.rememberTrackedLazyListState
import kotlinx.coroutines.flow.Flow

@Composable
fun SearchScreen(
    state: SearchState,
    effects: Flow<SearchEffect>,
    onAction: (SearchAction) -> Unit
) {
    val listState = rememberTrackedLazyListState(
        position = state.listPosition,
        onPositionChanged = { onAction(SearchAction.SaveScroll(it)) }
    )

    ObserveEffects(effects) { effect ->
        if (effect is SearchEffect.ScrollToTop) {
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
            Column {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try \"studio\", \"workflow\", or intentionally type \"error\" to see the retry path.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onAction(SearchAction.QueryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search creators, clips, tags") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onAction(SearchAction.SubmitSearch) }
                    )
                )
            }
        }

        when {
            state.isLoading && state.results.isEmpty() -> {
                item {
                    LoadingPane(
                        title = "Searching",
                        subtitle = "Running a fake query through the demo corpus."
                    )
                }
            }

            state.errorMessage != null && state.results.isEmpty() -> {
                item {
                    ErrorPane(
                        title = "Search hit a fake backend issue",
                        subtitle = state.errorMessage,
                        retryLabel = "Retry",
                        onRetry = { onAction(SearchAction.Retry) }
                    )
                }
            }

            state.submittedQuery.isBlank() -> {
                item {
                    EmptyPane(
                        title = "Search is ready",
                        subtitle = "Queries live in reducer state so tab switches keep your text and results."
                    )
                }
            }

            state.results.isEmpty() -> {
                item {
                    EmptyPane(
                        title = "No results for \"${state.submittedQuery}\"",
                        subtitle = "Try a broader keyword like creator, studio, or workflow."
                    )
                }
            }

            else -> {
                items(
                    items = state.results,
                    key = ClipCard::id
                ) { clip ->
                    ClipCardRow(
                        clip = clip,
                        onOpen = { onAction(SearchAction.OpenCreator(clip)) }
                    )
                }

                if (state.nextCursor != null) {
                    item {
                        LoadMoreFooter(
                            loading = state.isPaging,
                            onLoadMore = { onAction(SearchAction.LoadMore) }
                        )
                    }
                }
            }
        }
    }
}
