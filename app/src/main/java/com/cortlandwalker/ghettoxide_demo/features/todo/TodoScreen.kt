package com.cortlandwalker.ghettoxide_demo.features.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    state: TodoState,
    reducer: TodoReducer
) {
    var isAdding by remember { mutableStateOf(false) }
    var newTodo by remember { mutableStateOf(TextFieldValue("")) }

    fun startAdd() {
        isAdding = true
        newTodo = TextFieldValue("")
    }

    fun cancelAdd() {
        isAdding = false
        newTodo = TextFieldValue("")
    }

    fun saveTodo() {
        val trimmed = newTodo.text.trim()
        if (trimmed.isNotEmpty()) {
            reducer.postAction(TodoAction.Save(trimmed))
        }
        cancelAdd()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todos") },
                actions = {
                    TextButton(onClick = { startAdd() }) {
                        Text("Add")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(enabled = isAdding) {
                        cancelAdd()
                    }
            ) {
                if (state.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No todos yet. Tap Add to create one.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.items.filterNotNull()) { todo ->
                            TodoRow(
                                todoName = todo,
                                onTap = {
                                    reducer.postAction(TodoAction.TappedTodo(todo))
                                },
                                onDelete = {
                                    reducer.postAction(TodoAction.DeleteTodo(todo))
                                }
                            )
                        }
                    }
                }
            }

            if (isAdding) {
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = newTodo,
                        onValueChange = { newTodo = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New todo") },
                        singleLine = true,
                        keyboardActions = KeyboardActions(
                            onDone = { saveTodo() }
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { cancelAdd() }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { saveTodo() }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    todoName: String,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(todoName, style = MaterialTheme.typography.bodyLarge)

        TextButton(onClick = onDelete) {
            Text("Delete")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TodoScreenPreview() {
    val previewState = TodoState(
        items = listOf("Buy groceries", "Walk dog", "Pay bills")
    )

    MaterialTheme {
        TodoScreen(
            state = previewState,
            reducer = TodoReducer()
        )
    }
}
