![](logo/android-redux2.png)

# Ghettoxide

_An android `Architecture` based on Redux_

[![](https://jitpack.io/v/Cortlandd/Ghettoxide.svg)](https://jitpack.io/#Cortlandd/Ghettoxide)

# Installation
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
dependencies {
    implementation("com.github.Cortlandd:Ghettoxide:1.0.1")
}
```

### Reducer helpers

Every `Reducer<S, A, E>` gets a few built-ins:

- `state { current -> current.copy(...) }` – atomically update state.
- `emit(effect)` – push a one-off effect (navigation, toast, etc.).
- `postAction(action)` – enqueue another action from inside the reducer or UI.
- `currentState` – read the latest state snapshot.

Example:

```kotlin
class CounterReducer : Reducer<CounterState, CounterAction, CounterEffect>() {

    override suspend fun process(action: CounterAction) {
        when (action) {
            CounterAction.Increment -> {
                val before = currentState.count
                state { it.copy(count = before + 1) }
                emit(CounterEffect.ShowToast("value used to be $before but now is ${currentState.count}"))
            }
        }
    }
}

# Basic Example App Screenshots

| Default State        | Adding Todo            | Todo Item Added        | Toast when tapping Todo |
|----------------------|------------------------|------------------------|-------------------------|
| ![](samples/img.png) | ![](samples/img_1.png) | ![](samples/img_2.png) | ![](samples/img_3.png)  |

# Base Example (see app/ directory)

```kotlin
data class TodoState(
    var items: List<String?> = emptyList()
)

sealed interface TodoAction {
    data class Save(val todoName: String) : TodoAction
    data class DeleteTodo(val todoName: String) : TodoAction
    data class TappedTodo(val todoName: String) : TodoAction
}

sealed interface TodoEffect {
    data class ToastTodo(val todoName: String) : TodoEffect
}
```

```kotlin
class TodoReducer : Reducer<TodoState, TodoAction, TodoEffect>() {
    // Use this to kick off your first "load" event. Great for database, network calls, etc
    override fun onLoadAction(): TodoAction? {
        return null
    }

    override suspend fun process(action: TodoAction) {
        when (action) {
            is TodoAction.DeleteTodo -> {
                state {
                    val newItems = it.items.filter { it != action.todoName }
                    it.copy(items = newItems)
                }
            }
            is TodoAction.Save -> {
                state {
                    it.copy(items = it.items + action.todoName)
                }
            }
            is TodoAction.TappedTodo -> {
                emit(TodoEffect.ToastTodo(action.todoName))
            }
        }
    }
}
```

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    state: TodoState,
    // Typically this would be the following:
    // reducer: Reducer<TodoState, TodoAction, TodoEffect>
    // But as an example:
    dispatch: (TodoAction) -> Unit
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
            dispatch(TodoAction.Save(trimmed))
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

            // Background area above the input.
            // When we are in "adding" mode, tapping anywhere in this area cancels.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(enabled = isAdding) {
                        // Tapping outside the text field -> cancel
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
                                    dispatch(TodoAction.TappedTodo(todo))
                                },
                                onDelete = {
                                    dispatch(TodoAction.DeleteTodo(todo))
                                }
                            )
                        }
                    }
                }
            }

            if (isAdding) {
                Divider()

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
    // Fake placeholder state for the preview
    val previewState = TodoState(
        items = listOf("Buy groceries", "Walk dog", "Pay bills")
    )

    // Fake dispatch that does nothing (previews cannot run real reducers)
    val fakeDispatch: (TodoAction) -> Unit = {}

    MaterialTheme {
        TodoScreen(
            state = previewState,
            dispatch = fakeDispatch
        )
    }
}
```

Put it all together (typically we use a fragment but as an example here is the MainActivity version)
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create StoreViewModel using Ghettoxide's factory
        val store = ViewModelProvider(
            this,
            StoreViewModel.factory(
                initial = TodoState(),
                reducer = TodoReducer()
            )
        )[StoreViewModel::class.java] as StoreViewModel<TodoState, TodoAction, TodoEffect>

        setContent {
            GhettoxideTheme {
                val state by store.state.collectAsState()

                // observe effects
                LaunchedEffect(Unit) {
                    store.effects.collect { eff ->
                        when (eff) {
                            is TodoEffect.ToastTodo ->
                                Toast.makeText(this@MainActivity, eff.todoName, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                TodoScreen(
                    state = state,
                    dispatch = store::postAction
                )
            }
        }
    }
}
```

# Updating tag
```kotlin

git tag x.x.x
git push origin x.x.x
```