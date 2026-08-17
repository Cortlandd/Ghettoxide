package com.cortlandwalker.tabshowcase.features.profile.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cortlandwalker.tabshowcase.data.models.CollectionCard
import com.cortlandwalker.tabshowcase.ui.components.CollectionRow
import com.cortlandwalker.tabshowcase.ui.components.EmptyPane
import com.cortlandwalker.tabshowcase.ui.components.ErrorPane
import com.cortlandwalker.tabshowcase.ui.components.LoadingPane

@Composable
fun CollectionsScreen(
    state: CollectionsState,
    onAction: (CollectionsAction) -> Unit
) {
    when {
        state.isLoading -> {
            LoadingPane(
                title = "Loading Collections",
                subtitle = "This tab stays deliberately lightweight."
            )
        }

        state.errorMessage != null -> {
            ErrorPane(
                title = "Collections failed",
                subtitle = state.errorMessage,
                retryLabel = "Retry",
                onRetry = { onAction(CollectionsAction.Retry) }
            )
        }

        state.collections.isEmpty() -> {
            EmptyPane(
                title = "No collections yet",
                subtitle = "This nested tab keeps its own reducer and host state, even when it is empty."
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = state.collections,
                    key = CollectionCard::id
                ) { collection ->
                    CollectionRow(collection = collection)
                }
            }
        }
    }
}
