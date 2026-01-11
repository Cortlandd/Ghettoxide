# Ghettoxide Documentation

Ghettoxide provides a simple, opinionated, and lightweight Redux-like architecture for modern Android applications. It is designed to be easy to use, testable, and flexible enough to work with or without dependency injection.

## Core Components

- **Reducer**: Processes actions, mutates state, and emits one-off effects. This is where all your business logic lives.
- **StoreViewModel**: Owns the `Reducer` and the state, providing a standard `ViewModel` interface to the UI.
- **ReducerFragment**: A base `Fragment` that simplifies the wiring between the `StoreViewModel` and the UI.

## Basic Usage

### 1. Define State, Action, and Effect

```kotlin
// State: An immutable data class representing your screen's state.
data class TodoState(
    val items: List<String> = emptyList(),
    val isLoading: Boolean = false
)

// Action: A sealed interface representing all possible user/system actions.
sealed interface TodoAction {
    data object Load : TodoAction
    data class Add(val item: String) : TodoAction
}

// Effect: A sealed interface for one-off events like navigation or toasts.
sealed interface TodoEffect {
    data class ShowToast(val message: String) : TodoEffect
}
```

### 2. Create a Reducer

```kotlin
class TodoReducer : Reducer<TodoState, TodoAction, TodoEffect>() {

    override fun onLoadAction(): TodoAction? = TodoAction.Load

    override suspend fun process(action: TodoAction) {
        when (action) {
            is TodoAction.Load -> {
                // Load initial data
            }
            is TodoAction.Add -> {
                val updatedItems = currentState.items + action.item
                state { it.copy(items = updatedItems) }
                emit(TodoEffect.ShowToast("Added '''${action.item}'''"))
            }
        }
    }
}
```

### 3. Create a Fragment

**Without Dependency Injection**

```kotlin
class TodoFragment : ReducerFragment<TodoState, TodoAction, TodoEffect, TodoReducer>() {

    override var reducer = TodoReducer()
    override val initialState = TodoState()

    override fun onEffect(effect: TodoEffect) {
        when (effect) {
            is TodoEffect.ShowToast -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ...
        // Post actions to the reducer
        // myButton.setOnClickListener { reducer.postAction(TodoAction.Add("New Item")) }
        // ...
    }
}
```

**With Hilt**

```kotlin
@AndroidEntryPoint
class TodoFragment : ReducerFragment<TodoState, TodoAction, TodoEffect, TodoReducer>() {

    @Inject
    override lateinit var reducer: TodoReducer
    override val initialState = TodoState()

    // ... (onEffect and onCreateView are the same)
}
```

## Testing

Ghettoxide is designed to be easily testable. The `testBind` function allows you to test a `Reducer` in isolation.

```kotlin
@Test
fun `Add action should add item to state and emit toast effect`() = runTest {
    // Given
    val reducer = TodoReducer()
    val effects = mutableListOf<TodoEffect>()
    reducer.testBind(
        initialState = TodoState(),
        effects = effects
    )

    // When
    reducer.accept(TodoAction.Add("New Item"))

    // Then
    val state = reducer.currentState
    assertThat(state.items).containsExactly("New Item")
    assertThat(effects).containsExactly(TodoEffect.ShowToast("Added '''New Item'''"))
}
```
