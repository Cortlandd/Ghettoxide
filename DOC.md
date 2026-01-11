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

### Testing Utilities Explained

To write effective tests, you'll primarily use three testing-specific APIs:

1.  **`reducer.testBind(initialState, effects, scope)`**: This is an extension function that prepares your `Reducer` for testing. It simulates the `StoreViewModel`'s role by providing a simple, in-memory state container.
    *   `initialState`: The starting state for your test.
    *   `effects`: An optional `MutableList<Effect>` that will automatically capture any effects your reducer emits with the `emit()` function.
    *   `scope`: An optional `CoroutineScope`. By default, it uses an `Unconfined` dispatcher.

2.  **`reducer.accept(Action)`**: This is the entry point for sending an action to the reducer in a test. It directly calls the `process` method within the reducer's `Mutex`, ensuring that actions are processed sequentially. It is the test-equivalent of calling `vm.postAction()` from the UI.

3.  **`reducer.currentState`**: This property gives you a direct, synchronous snapshot of the state at any point in your test. You use this *after* calling `accept()` to assert that your reducer logic produced the expected outcome.

### Example Test Setup (Synchronous Tests)

For most tests, which cover synchronous logic, the setup is straightforward. Call `testBind` in your `@Before` block and you're done.

```kotlin
@ExperimentalCoroutinesApi
class UpsertWorkoutReducerTest {

    private lateinit var mockRepo: WorkoutRepository
    private lateinit var reducer: UpsertWorkoutReducer
    private lateinit var effects: MutableList<UpsertWorkoutEffect>

    @Before
    fun setUp() {
        mockRepo = mockk(relaxed = true)
        reducer = UpsertWorkoutReducer(mockRepo)
        effects = mutableListOf()

        reducer.testBind(
            initialState = UpsertWorkoutState(),
            effects = effects
        )
    }

    @Test
    fun `SaveClicked with blank name should show error`() = runTest {
        // When
        reducer.accept(UpsertWorkoutAction.SaveClicked)

        // Then
        assertThat(effects.first()).isInstanceOf(UpsertWorkoutEffect.ShowError::class.java)
    }
}
```

### Testing Asynchronous Logic

When your reducer launches coroutines (e.g., to collect a `Flow`), you need to ensure your test can control that asynchronous work. The recommended pattern is to keep your `testBind` in the `@Before` block, and for your async tests, use `reducer.attachScope(this)`.

`attachScope` is an `internal` function that replaces the reducer's scope. By passing the `TestScope` from `runTest` (available as `this`), you ensure the reducer's async work runs on the same scheduler as your test.

```kotlin
@ExperimentalCoroutinesApi
class WorkoutListReducerTest {
    // ...
    @Before
    fun setUp() {
        // ... (the same as the synchronous example)
        reducer.testBind(
            initialState = WorkoutListState(),
            effects = effects
        )
    }

    @Test
    fun `OnLoad should collect workouts and update state`() = runTest { // `this` is a TestScope
        // GIVEN
        // For this async test, replace the default scope with the test's scope
        reducer.attachScope(this)

        val workouts = listOf(Workout("1", "Test"))
        coEvery { mockRepo.observeAll() } returns flowOf(workouts)

        // WHEN
        reducer.accept(WorkoutListAction.OnLoad)

        // THEN
        // No `advanceUntilIdle()` needed, `runTest` handles it automatically.
        val state = reducer.currentState
        assertThat(state.workouts).isEqualTo(workouts)
    }
}
```
