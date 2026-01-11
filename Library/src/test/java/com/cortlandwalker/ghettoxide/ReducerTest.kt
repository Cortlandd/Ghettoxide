package com.cortlandwalker.ghettoxide

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// --- Test Fixtures ---
data class TestState(val count: Int = 0, val items: List<String> = emptyList())

sealed interface TestAction {
    data object Increment : TestAction
    data object Decrement : TestAction
    data class AddItem(val item: String) : TestAction
    data object LoadItems : TestAction
    data object PostAnotherAction : TestAction
}

sealed interface TestEffect {
    data class ShowToast(val message: String) : TestEffect
}

class TestReducer(private val testName: String = "") :
    Reducer<TestState, TestAction, TestEffect>() {

    override fun onLoadAction(): TestAction? = null

    override suspend fun process(action: TestAction) {
        when (action) {
            TestAction.Increment -> {
                state { it.copy(count = it.count + 1) }
            }
            TestAction.Decrement -> {
                state { it.copy(count = it.count - 1) }
            }
            is TestAction.AddItem -> {
                val newItems = currentState.items + action.item
                state { it.copy(items = newItems) }
                emit(TestEffect.ShowToast("Added ${action.item}"))
            }
            TestAction.LoadItems -> {
                scope.launch {
                    delay(100) // Simulate network call
                    state { it.copy(items = listOf("item1", "item2")) }
                }
            }
            TestAction.PostAnotherAction -> {
                postAction(TestAction.Increment)
            }
        }
    }
}

// --- Tests ---
@ExperimentalCoroutinesApi
class ReducerTest {

    private lateinit var reducer: TestReducer
    private lateinit var effects: MutableList<TestEffect>
    private lateinit var postedActions: MutableList<TestAction>

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        reducer = TestReducer()
        effects = mutableListOf()
        postedActions = mutableListOf()
        reducer.testBind(
            initialState = TestState(),
            effects = effects,
            postedActions = postedActions
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================================================================
    // 1. Tests for Ghettoxide's core logic
    // ==================================================================

    @Test
    fun `accept should process action and update state`() = runTest {
        // When
        reducer.accept(TestAction.Increment)

        // Then
        assertThat(reducer.currentState.count).isEqualTo(1)
    }

    @Test
    fun `emit should add effect to the effects list`() = runTest {
        // When
        reducer.accept(TestAction.AddItem("test"))

        // Then
        assertThat(effects).containsExactly(TestEffect.ShowToast("Added test"))
    }

    @Test
    fun `postAction should add action to the postedActions list`() = runTest {
        // When
        reducer.accept(TestAction.PostAnotherAction)

        // Then
        assertThat(postedActions).containsExactly(TestAction.Increment)
    }

    // ==================================================================
    // 2. Tests illustrating patterns from DOC.md
    // ==================================================================

    @Test
    fun `DOC - Synchronous logic test`() = runTest {
        // This test demonstrates the simplest case from the docs.
        // `testBind` is called in `setUp` and we just accept an action.

        // When
        reducer.accept(TestAction.Increment)
        reducer.accept(TestAction.Increment)
        reducer.accept(TestAction.Decrement)

        // Then
        val state = reducer.currentState
        assertThat(state.count).isEqualTo(1)
        assertThat(effects).isEmpty()
    }

    @Test
    fun `DOC - Asynchronous logic test`() = runTest {
        // This test demonstrates the async pattern from the docs.

        // GIVEN
        // For this async test, we replace the reducer's scope with the test's scope.
        reducer.attachScope(this)

        // WHEN
        reducer.accept(TestAction.LoadItems)
        advanceUntilIdle() // <-- FIX: Run the coroutine launched in the reducer

        // THEN
        val state = reducer.currentState
        assertThat(state.items).isEqualTo(listOf("item1", "item2"))
    }
}
